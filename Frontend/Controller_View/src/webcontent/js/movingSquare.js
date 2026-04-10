const game = new Vue({

  el: "#game",
  
  data: {
    connection : null
  },

  methods : {
    keyMove(key, isPressed) {
      if (key === "z") this.sendMessage("HAUT");
      if (key === "s") this.sendMessage("BAS");
      if (key === "q") this.sendMessage("GAUCHE");
      if (key === "d") this.sendMessage("DROITE");
    },

    sendMessage: function(message) {
      console.log(this.connection);
      this.connection.send(message);
    },

    updateState: function(data) {
      for (const [key, coords] of Object.entries(data)) {
        const element = this.$el.querySelector(`#s${key}`);

        if (element === null) {
          // créer un div
          const child = document.createElement('div')

          // ajouter un attribut
          child.setAttribute('id', `s${key}`)
          child.setAttribute('class', 'squareBox')

          // l'ajouter au parent
          this.$el.appendChild(child)
        } else {
          let top1 = coords[1];
          let left1 = coords[0];
          
          element.style.top = top1 + "px",
          element.style.left = left1 + "px"
        }
      }
    },
  },

  created : function() {
    console.log("Starting connection to WebSocket Server")
    this.connection = new WebSocket("ws://localhost:8080/Controller_View/GameEndpoint/ws")

    this.connection.onmessage = function(event) {
      console.log(event);

      game.updateState(JSON.parse(event.data)["data"]);
    }

    this.connection.onopen = function(event) {
      console.log(event)
      console.log("Successfully connected to the echo websocket server...")

      game.updateState(JSON.parse(event.data)["data"]);
    }
  },

  mounted() {

    window.addEventListener("keydown", (e) => {
      game.keyMove(e.key, 1);
    });

  }
});

