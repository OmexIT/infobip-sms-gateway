(defproject infobip-sms-gateway "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :min-lein-version "2.0.0"
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [compojure "1.5.0"]
                 [ring/ring-defaults "0.1.5"]
                 [ring/ring-core "1.4.0"]
                 [ring/ring-jetty-adapter "1.4.0"]
                 [cheshire "5.4.0"]
                 [org.slf4j/slf4j-log4j12 "1.7.6"]
                 [mysql/mysql-connector-java "5.1.32"]
                 [c3p0/c3p0 "0.9.1.2"]
                 [org.clojure/tools.logging "0.3.1"]
                 [jobesque "0.0.2"]
                 [environ "1.0.0"]
                 [slingshot "0.12.2"]
                 [com.googlecode.libphonenumber/libphonenumber "7.4.0"]
                 [javax.servlet/servlet-api "2.5"]
                 [ring/ring-json "0.4.0"]
                 [clj-http "2.2.0"]
                 [de.ubercode.clostache/clostache "1.4.0"]
                 [ring-cors "0.1.8"]
                 [clj-time "0.11.0"]
                 [com.novemberain/langohr "3.5.0"]
                 [org.clojure/java.jdbc "0.6.1"]
                 [clojurewerkz/meltdown "1.1.0"]]

  :jvm-opts ["-Dinfobip.service.config=E:\\PROJECTS\\Uganda\\infobip-sms-gateway\\service.config"]

  :plugins [[lein-ring "0.9.7"]
            [lein-environ "1.0.0"]]

  :ring {:init         com.omexit.infobip.handler/init-sms-app
         :handler      com.omexit.infobip.handler/app
         :uberwar-name "infobip-sms-gateway.war"}
  :profiles
  {:dev {:dependencies [[javax.servlet/servlet-api "2.5"]
                        [ring/ring-mock "0.3.0"]]}})
