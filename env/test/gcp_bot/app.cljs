(ns gcp-bot.app
  (:require
    [doo.runner :refer-macros [doo-tests]]
    [gcp-bot.core-test]))

(doo-tests 'gcp-bot.core-test)


