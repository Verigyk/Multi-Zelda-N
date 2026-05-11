const game = new Vue({

  el: "#game",
  
  data: {
    connection : null,
    matchId: new URLSearchParams(window.location.search).get("matchId")
  },

  methods : {
    keyMove(key, isPressed) {
      if (key === "z") this.sendMessage("HAUT");
      if (key === "s") this.sendMessage("BAS");
      if (key === "q") this.sendMessage("GAUCHE");
      if (key === "d") this.sendMessage("DROITE");
      if (key === "p") this.sendMessage("ATTAQUE");
    },

    sendMessage: function(message) {
      if (this.connection && this.connection.readyState === WebSocket.OPEN) {
        this.connection.send(message);
      }
    },

    updateState: function(data) {
      switch (data["type"]) {
        case "Players":
          game.updatePositions(data["data"]);
          break;
        case "RemovePlayers":
          game.removePlayers(data["data"]);
          break;
      }
    },

    updatePositions: function(data) {
      for (const [key, coords] of Object.entries(data)) {
        const element = this.$el.querySelector(`#s${key}`);

        let top = coords[1];
        let left = coords[0];

        if (element === null) {
          // créer un div
          const child = document.createElement('div')

          // ajouter un attribut
          child.setAttribute('id', `s${key}`)
          child.setAttribute('class', 'squareBox')

          child.style.top = top + "px",
          child.style.left = left + "px"

          // l'ajouter au parent
          this.$el.appendChild(child)
        } else {
          element.style.top = top + "px",
          element.style.left = left + "px"
        }
      }
    },

    removePlayers: function(data) {
      console.log(data)
      for (const id of data) {
        this.$el.querySelector(`#s${id}`).remove();
      }
    }
  },

  created : function() {
    if (!this.matchId) {
      console.error("Missing matchId in URL");
      return;
    }

    console.log("Starting connection to WebSocket Server")
    const protocol = window.location.protocol === "https:" ? "wss" : "ws";
    const context = window.location.pathname.split("/")[1] || "Controller_View";
    this.connection = new WebSocket(`${protocol}://${window.location.host}/${context}/GameEndpoint/${encodeURIComponent(this.matchId)}/ws`)

    this.connection.onmessage = function(event) {
      console.log(event);

      game.updateState(JSON.parse(event.data));
    }

    this.connection.onopen = function(event) {
      console.log(event)
      console.log("Successfully connected to the echo websocket server...")
    }
  },

  mounted() {

    window.addEventListener("keydown", (e) => {
      game.keyMove(e.key, 1);
    });

  }
});
