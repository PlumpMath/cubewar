(ns cassiel.cubewar.server
  "Main server."
  (:require (cassiel.cubewar [cube :as c]
                             [players :as pl]
                             [tournament :as t]
                             [network :as net])))

;; Deal with incoming OSC messages. The multimethod dispatches on the
;; third argument (the address of the OSC message, turned into a keyword)
;; to determine the function.

(defmulti service (fn [& args] (nth args 3)))

;; `service` takes the world, the dispatch argument, and a list
;; of arguments that came in with the OSC message. The result is a new world
;; and a journal.

;; Attach a player. `sources->names` maps incoming `{:host, :port}` to name.
;; `names->transmitters` maps names to actual transmitters.

(defmethod service :attach
  [world origin _player _ [player-name back-port]]
  (let [sources->names (assoc (:sources->names world)
                         origin
                         player-name)
        names->transmitters (assoc (:names->transmitters world)
                              player-name
                              (net/start-transmitter (:host origin) back-port))]
    {:world (assoc world
              :sources->names sources->names
              :names->transmitters names->transmitters)
     :journal [{:to player-name :action :attached :args [(:host origin) back-port]}]}))

(defmethod service :handshake
  [world origin player & _]
  {:world world :journal [{:to player :action :handshake-reply :args []}]})

(defmethod service :fire
  [world origin player & _]
  (t/fire world player))

(defmethod service :pitch-up
  [world origin player & _]
  (t/move world player c/pitch-up))

(defmethod service :yaw-right
  [world origin player & _]
  (t/move world player c/yaw-right))

(defn retrieve-player
  [world origin]
  (get (:sources->names world) origin))

(defn retrieve-transmitter
  [world player]
  (get (:names->transmitters world) player))

(defn serve1
  "The `full-state` is an atom with map containing `:world` and `:journal`. The
   journal is effectively transient, but we need some way to return it atomically
   so that it can be transmitted. (TODO we need a way to do that safely.)"
  [full-state origin action args]

  (swap! full-state
         (fn [{w :world}] (service w origin (retrieve-player w origin) action args))))

;; Actual server.

(defn start-game
  [port]

  ;; Test with strings for players - these are directly in the OSC at the moment.
  (let [arena (-> {}
                  (pl/add-player "P1" (pl/gen-player [0 0 0]))
                  (pl/add-player "P2" (pl/gen-player [1 0 0]))
                  (pl/add-player "P3" (pl/gen-player [0 1 0])))
        scoring {"P1" 50 "P2" 50 "P3" 50}
        FULL-STATE (atom {:world {:arena arena :scoring scoring :back-links {}}
                          :journal []})]

    ;; The watch runs through the journal, picking out the destination transmitter via `:to`,
    ;; making a `Message` out of the rest of each entry, and transmitting.
    (letfn [(watcher [k r old new]
              (println (:journal new))
              (doseq [x (:journal new)]
                (let [tx (retrieve-transmitter (:world new) (:to x))
                      msg (net/make-message (:action x) (:args x))]
                  (.transmit tx msg))))]
      (add-watch FULL-STATE :key watcher))

    {:receiver (net/start-receiver 8123 (partial serve1 FULL-STATE))
     :state FULL-STATE}))
