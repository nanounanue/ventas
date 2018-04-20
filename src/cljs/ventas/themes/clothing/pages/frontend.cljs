(ns ventas.themes.clothing.pages.frontend
  (:require
   [ventas.components.base :as base]
   [ventas.components.category-list :refer [category-list]]
   [ventas.i18n :refer [i18n]]
   [ventas.plugins.featured-categories.core :as plugins.featured-categories]
   [ventas.plugins.featured-products.core :as plugins.featured-products]
   [ventas.plugins.slider.core :as plugins.slider]
   [ventas.routes :as routes]
   [ventas.themes.clothing.components.header :refer [header]]
   [ventas.themes.clothing.components.heading :as theme.heading]
   [ventas.themes.clothing.components.preheader :refer [preheader]]
   [ventas.themes.clothing.components.skeleton :refer [skeleton]]
   [ventas.themes.clothing.pages.frontend.cart]
   [ventas.themes.clothing.pages.frontend.favorites]
   [ventas.themes.clothing.pages.frontend.category]
   [ventas.themes.clothing.pages.frontend.checkout]
   [ventas.themes.clothing.pages.frontend.login]
   [ventas.themes.clothing.pages.frontend.privacy-policy]
   [ventas.themes.clothing.pages.frontend.product]
   [ventas.themes.clothing.pages.frontend.profile]
   [ventas.utils.logging :refer [debug error info trace warn]]
   [re-frame.core :as rf]))

(def slider-kw :sample-slider)

(defn page []
  [skeleton
   [:div
    [plugins.slider/slider slider-kw]
    [base/container
     [category-list]
     [theme.heading/heading (i18n ::suggestions-of-the-week)]
     [plugins.featured-products/featured-products]
     [theme.heading/heading (i18n ::recently-added)]
     [plugins.featured-categories/featured-categories]]]])

(rf/reg-event-fx
 ::init
 (fn [_ _]
   {:dispatch-n [[::plugins.featured-categories/featured-categories.list]
                 [::plugins.featured-products/featured-products.list]
                 [::plugins.slider/sliders.get slider-kw]]}))

(routes/define-route!
  :frontend
  {:name ::page
   :url ""
   :component page
   :init-fx [::init]})
