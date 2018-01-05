(ns ventas.entities.i18n
  (:require
   [clojure.spec.alpha :as spec]
   [ventas.database.entity :as entity]
   [ventas.utils :as utils]
   [clojure.test.check.generators :as gen]
   [ventas.database.generators :as generators]
   [ventas.database :as db]))

(spec/def :i18n.culture/keyword ::generators/keyword)

(spec/def :i18n.culture/name ::generators/string)

(spec/def :schema.type/i18n.culture
  (spec/keys :req [:i18n.culture/keyword
                   :i18n.culture/name]))

(entity/register-type!
 :i18n.culture
 {:attributes
  [{:db/ident :i18n.culture/keyword
    :db/valueType :db.type/keyword
    :db/cardinality :db.cardinality/one
    :db/unique :db.unique/identity}
   {:db/ident :i18n.culture/name
    :db/valueType :db.type/string
    :db/cardinality :db.cardinality/one}]

  :fixtures
  (fn []
    [{:i18n.culture/keyword :en_US
      :i18n.culture/name "English (US)"}
     {:i18n.culture/keyword :es_ES
      :i18n.culture/name "Español (España)"}])

  :to-json
  (fn [this _]
    (:i18n.culture/keyword this))

  :from-json
  (fn [this]
    (entity/find [:i18n.culture/keyword this]))

  :seed-number 0
  :autoresolve? true})


(spec/def :i18n.translation/value ::generators/string)

(spec/def :i18n.translation/culture
  (spec/with-gen ::entity/ref #(entity/ref-generator :i18n.culture)))

(spec/def :schema.type/i18n.translation
  (spec/keys :req [:i18n.translation/value
                   :i18n.translation/culture]))

(entity/register-type!
 :i18n.translation
 {:attributes
  [{:db/ident :i18n.translation/value
    :db/valueType :db.type/string
    :db/cardinality :db.cardinality/one}
   {:db/ident :i18n.translation/culture
    :db/valueType :db.type/ref
    :db/cardinality :db.cardinality/one}]

  :dependencies
  #{:i18n.culture}

  :to-json
  (fn [this _]
    [(:i18n.culture/keyword (entity/find (:i18n.translation/culture this)))
     (:i18n.translation/value this)])})

(defn translations-generator-for-culture [culture-id]
  (->> (entity/generate :i18n.translation)
       (map #(assoc % :i18n.translation/culture culture-id))
       (gen/elements)))

(defn translations-generator []
  (let [culture-ids (map :db/id (entity/query :i18n.culture))]
    (apply gen/tuple
           (remove nil?
                   (map translations-generator-for-culture culture-ids)))))

(spec/def :i18n/translations
  (spec/with-gen ::entity/refs
                 translations-generator))

(spec/def :schema.type/i18n
  (spec/keys :req [:i18n/translations]))

(entity/register-type!
 :i18n
 {:attributes
  [{:db/ident :i18n/translations
    :db/valueType :db.type/ref
    :db/cardinality :db.cardinality/many
    :db/isComponent true}]

  :dependencies
  #{:i18n.translation}

  :before-create
  (fn [{:i18n/keys [translations]}]
    (let [entities (concat (->> (filter number? translations)
                                (map entity/find))
                           (filter map? translations))]
      (when (->> entities
                 (map :i18n.translation/culture)
                 (utils/has-duplicates?))
        (throw (Error. "You can't add more than one translation per culture to an :i18n entity")))))

  :to-json
  (fn [this {:keys [culture]}]
    {:pre [(or (not culture) (utils/check ::entity/ref culture))]}
    (if-not culture
      (->> (:i18n/translations this)
           (map (comp entity/to-json entity/find))
           (into {}))
      (-> (db/q '[:find ?translated
                  :in $ ?this-eid ?culture
                  :where
                  [?this-eid :i18n/translations ?term-translation]
                  [?term-translation :i18n.translation/value ?translated]
                  [?term-translation :i18n.translation/culture ?culture]]
                [(:db/id this) culture])
          (first)
          (first))))

  :autoresolve? true})

(spec/def ::ref
  (spec/with-gen ::entity/ref #(entity/ref-generator :i18n :new? true)))

(defn get-i18n-entity [translations]
  {:schema/type :schema.type/i18n
   :i18n/translations (map (fn [[culture-kw value]]
                             {:schema/type :schema.type/i18n.translation
                              :i18n.translation/value value
                              :i18n.translation/culture [:i18n.culture/keyword culture-kw]})
                           translations)})