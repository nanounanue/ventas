(ns ventas.events
  "Application-wide events"
  (:require
   [day8.re-frame.forward-events-fx]
   [re-frame.core :as rf]
   [ventas.server.api :as backend]
   [ventas.server.api.admin :as api.admin]
   [ventas.server.api.user :as api.user]
   [ventas.i18n :refer [i18n]]
   [ventas.utils.logging :as log]
   [ventas.session :as session])
  (:require-macros
   [ventas.events]))

(rf/reg-sub
 :db
 (fn [db [_ where]]
   {:pre [(sequential? where)]}
   (get-in db where)))

(rf/reg-event-db
 :db
 (fn [db [_ where what]]
   {:pre [(sequential? where)]}
   (log/debug :db where what)
   (assoc-in db where what)))

(rf/reg-event-db
 :db.update
 (fn [db [_ where what-fn]]
   {:pre [(ifn? what-fn) (sequential? where)]}
   (log/debug :db.update where what-fn)
   (update-in db where what-fn)))

(rf/reg-event-fx
 ::categories.list
 (fn [_ _]
   {:dispatch [::backend/categories.list
               {:success [:db [:categories]]}]}))

(rf/reg-event-fx
 ::configuration.get
 (fn [_ [_ k-or-ks]]
   {:dispatch [::backend/configuration.get
               {:params k-or-ks
                :success ::configuration.get.next}]}))

(rf/reg-event-db
 ::configuration.get.next
 (fn [db [_ data]]
   (update db :configuration #(merge % data))))

(rf/reg-event-fx
 ::enums.get
 (fn [_ [_ type next-event]]
   {:dispatch [::backend/enums.get
               {:params {:type type}
                :success
                (fn [options]
                  (let [values (map (fn [{:keys [name id ident]}]
                                      {:text name
                                       :value id
                                       :ident ident})
                                    options)]
                    (rf/dispatch [:db [:enums type] values])
                    (when next-event
                      (rf/dispatch (conj next-event values)))))}]}))

(rf/reg-event-fx
 ::entities.sync
 (fn [_ [_ eid]]
   {:dispatch [::backend/entities.find eid
               {:sync true
                :success [:db [:entities eid]]}]}))

(rf/reg-event-fx
 ::image-sizes.list
 (fn [_ [_]]
   {:dispatch [::backend/image-sizes.list
               {:success [:db [:image-sizes]]}]}))

(rf/reg-event-fx
 ::admin.entities.sync
 (fn [_ [_ eid]]
   {:dispatch [::api.admin/admin.entities.find
               {:sync true
                :params {:id eid}
                :success [:db [:admin-entities eid]]}]}))

(rf/reg-event-fx
 ::admin.entities.remove
 (fn [_ [_ db-path eid]]
   {:dispatch [::api.admin/admin.entities.remove
               {:params {:id eid}
                :success [::admin.entities.remove.next db-path eid]}]}))

(rf/reg-event-db
 ::admin.entities.remove.next
 (fn [db [_ db-path eid]]
   (update-in db db-path (fn [entities]
                           (remove #(= (:id %) eid)
                                   entities)))))

(rf/reg-event-fx
 ::i18n.cultures.list
 (fn [_ _]
   {:dispatch [::backend/i18n.cultures.list
               {:success ::i18n.cultures.list.next}]}))

(rf/reg-event-db
 ::i18n.cultures.list.next
 (fn [db [_ cultures]]
   (->> cultures
        (map (fn [culture]
               {:text (:name culture)
                :value (:id culture)
                :keyword (:keyword culture)}))
        (assoc db :cultures))))

(rf/reg-event-fx
 ::users.register
 (fn [_ [_ {:keys [name email password]}]]
   {:dispatch [::backend/users.register
               {:params {:name name
                         :email email
                         :password password}
                :success ::session.start}]}))

(rf/reg-event-fx
 ::users.login
 (fn [_ [_ {:keys [email password]}]]
   {:dispatch [::backend/users.login
               {:params {:email email
                         :password password}
                :success ::session.start}]}))

(rf/reg-event-fx
 ::users.session
 [(rf/inject-cofx :local-storage)
  (rf/inject-cofx :url)]
 (fn [{:keys [local-storage url]} [_]]
   (let [token (or (get-in url [:query "token"]) (:token local-storage))]
     {:dispatch [::backend/users.session
                 {:params {:token token}
                  :success ::session.start
                  :error ::session.error}]})))

(rf/reg-event-fx
 ::users.logout
 (fn [_ _]
   {:dispatch [::backend/users.logout
               {:success ::session.stop}]}))

(rf/reg-event-fx
 ::session.start
 [(rf/inject-cofx :local-storage)]
 (fn [{:keys [db local-storage]} [_ {:keys [user token]}]]
   (merge
    {:db (assoc-in db [:session :identity] user)}
    (when token
      {:local-storage (assoc local-storage :token token)}))))

(rf/reg-event-fx
 ::session.stop
 [(rf/inject-cofx :local-storage)]
 (fn [{:keys [local-storage db]}]
   {:db (dissoc db :session)
    :local-storage (dissoc local-storage :token)}))

(rf/reg-event-db
 ::session.error
 (fn [db _]
   (assoc db :session {})))

(rf/reg-event-fx
 ::shipping-methods.list
 (fn [_ _]
   {:dispatch [::backend/shipping-methods.list
               {:success [:db [:shipping-methods]]}]}))

(rf/reg-event-fx
 ::users.favorites.enumerate
 (fn [_ _]
   {:dispatch [::api.user/users.favorites.enumerate
               {:success [:db [:users.favorites]]}]}))

(rf/reg-event-fx
 ::users.favorites.toggle
 (fn [{:keys [db]} [_ id]]
   (let [favorites (set (get db :users.favorites))]
     {:dispatch (if (contains? favorites id)
                  [::backend/users.favorites.remove
                   {:params {:id id}
                    :success (fn [favorites]
                               (rf/dispatch [:db.update :users.favorites #(disj (set favorites) id)]))}]
                  [::backend/users.favorites.add
                   {:params {:id id}
                    :success (fn [favorites]
                               (rf/dispatch [:db.update :users.favorites #(conj (set favorites) id)]))}])
      :db (update db :users.favorites #(if (contains? favorites id)
                                         (disj (set %) id)
                                         (conj (set %) id)))})))

(rf/reg-sub
 ::users.favorites.favorited?
 (fn [_]
   (rf/subscribe [:db [:users.favorites]]))
 (fn [favorites [_ id]]
   (contains? (set favorites) id)))

(rf/reg-event-fx
 ::upload
 (fn [_ [_ {:keys [success file]}]]
   (let [fr (js/FileReader.)]
     (set! (.-onload fr) #(rf/dispatch [:effects/ws-upload-request
                                        {:array-buffer (-> fr .-result)
                                         :filename (.-name file)
                                         :success success}]))
     (.readAsArrayBuffer fr file))))
