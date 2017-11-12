(ns ventas.entities.product-taxonomy
  (:require
   [clojure.spec.alpha :as spec]
   [ventas.database :as db]
   [ventas.database.entity :as entity]
   [ventas.entities.i18n :as entities.i18n]))

(spec/def :product.taxonomy/name ::entities.i18n/ref)

(spec/def :schema.type/attribute
  (spec/keys :req [:product.taxonomy/name]))

(entity/register-type!
 :product.taxonomy
 {:attributes
  [{:db/ident :product.taxonomy/name
    :db/valueType :db.type/ref
    :db/cardinality :db.cardinality/one
    :db/isComponent true}]})