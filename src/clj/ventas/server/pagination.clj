(ns ventas.server.pagination
  (:require
   [clojure.spec.alpha :as spec]
   [ventas.utils :as utils]))

(defn- limit [coll offset quantity]
  (let [offset (or offset 0)]
    (take quantity (drop offset coll))))

(spec/def ::page number?)
(spec/def ::items-per-page number?)
(spec/def ::pagination
  (spec/keys :req-un [::page ::items-per-page]))

(defn paginate [coll {:keys [items-per-page page] :as pagination}]
  {:pre [(or (nil? pagination) (utils/check ::pagination pagination))]}
  (if pagination
    {:total (count coll)
     :items (limit coll
                   (* items-per-page page)
                   items-per-page)}
    coll))

(defn wrap-paginate [previous]
  (let [pagination (get-in previous [:request :params :pagination])]
    (-> previous
        (update :response #(paginate % pagination)))))

(defn- sort* [coll {:keys [field direction]}]
  (let [sorter (if (sequential? field)
                 #(get-in % field)
                 field)
        sorted (sort-by sorter coll)]
    (if (= direction :asc)
      sorted
      (reverse sorted))))

(defn wrap-sort [previous]
  (let [config (get-in previous [:request :params :sorting])]
    (if (or (not (sequential? (:response previous)))
            (not config))
      previous
      (-> previous
          (update :response #(sort* % config))))))
