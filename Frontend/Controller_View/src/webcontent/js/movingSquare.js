const game = new Vue({

  el: "#game",
  
  data: {
    connection : null,
    matchId: new URLSearchParams(window.location.search).get("matchId"),
    apiBase: `${window.location.origin}/facade`,
    playerId: null,
    ready: false,
    roomState: "WAITING",
    playerGems: 0,
    remainingSeconds: 180,
    winnerName: "",
    quitting: false
  },

  methods : {
    keyMove(key, isPressed) {
      if (this.roomState !== "RUNNING") return;
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
        case "YouAre":
          game.playerId = data["playerId"];
          break;
        case "RoomState":
          game.updateRoomState(data);
          break;
        case "Gems":
          game.updateGems(data["data"]);
          break;
      }
    },

    updateRoomState: function(data) {
      this.roomState = data["state"];
      if (this.playerId && data["ready"] && data["ready"][this.playerId] !== undefined) {
        this.ready = data["ready"][this.playerId];
      }

      const matchState = document.getElementById("matchState");
      const readyState = document.getElementById("readyState");
      const readyBtn = document.getElementById("readyBtn");
      const timer = document.getElementById("timer");
      const winner = document.getElementById("winner");

      this.remainingSeconds = data["remainingSeconds"] !== undefined ? data["remainingSeconds"] : this.remainingSeconds;
      this.winnerName = data["winnerName"] || "";

      if (this.roomState === "FINISHED") {
        matchState.textContent = "Game finished";
        readyState.textContent = "Finished";
      } else {
        matchState.textContent = this.roomState === "RUNNING" ? "Game started" : "Waiting for players";
        readyState.textContent = this.ready ? "Ready" : "Not ready";
      }
      timer.textContent = this.formatTime(this.remainingSeconds);
      winner.textContent = this.winnerName ? `Winner: ${this.winnerName}` : "";
      readyBtn.disabled = this.ready || this.roomState === "RUNNING" || this.roomState === "FINISHED";
    },

    formatTime: function(totalSeconds) {
      const minutes = Math.floor(totalSeconds / 60);
      const seconds = totalSeconds % 60;
      return `${minutes}:${String(seconds).padStart(2, "0")}`;
    },

    updatePositions: function(data) {
      for (const [key, player] of Object.entries(data)) {
        const element = this.$el.querySelector(`#s${key}`);

        const top = Array.isArray(player) ? player[1] : player.y;
        const left = Array.isArray(player) ? player[0] : player.x;
        const color = Array.isArray(player) ? "red" : player.color;
        const gems = Array.isArray(player) ? 0 : player.gems;

        if (element === null) {
          const child = document.createElement('div')

          child.setAttribute('id', `s${key}`)
          child.setAttribute('class', 'squareBox')

          child.style.top = top + "px";
          child.style.left = left + "px";
          child.style.backgroundColor = color;

          this.$el.appendChild(child)
        } else {
          element.style.top = top + "px";
          element.style.left = left + "px";
          element.style.backgroundColor = color;
        }

        if (key === this.playerId) {
          this.playerGems = gems;
          document.getElementById("gemCount").textContent = String(this.playerGems);
        }
      }
    },

    updateGems: function(data) {
      const gemLayer = document.getElementById("gemLayer");
      const existing = new Set();

      for (const [key, gem] of Object.entries(data)) {
        existing.add(`gem-${key}`);
        let element = gemLayer.querySelector(`#gem-${key}`);
        if (element === null) {
          element = document.createElement("div");
          element.setAttribute("id", `gem-${key}`);
          element.setAttribute("class", "gem");
          gemLayer.appendChild(element);
        }
        element.style.left = gem.x + "px";
        element.style.top = gem.y + "px";
      }

      gemLayer.querySelectorAll(".gem").forEach((element) => {
        if (!existing.has(element.id)) {
          element.remove();
        }
      });
    },

    removePlayers: function(data) {
      console.log(data)
      for (const id of data) {
        const element = this.$el.querySelector(`#s${id}`);
        if (element) {
          element.remove();
        }
      }
    },

    leaveMatch: async function() {
      if (this.connection) {
        this.connection.close();
      }
    },

    leaveMatchOnUnload: function() {
      if (this.connection) {
        this.connection.close();
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

    this.connection.onclose = function() {
      if (!game.quitting) {
        console.log("Game websocket closed");
      }
    }
  },

  mounted() {
    document.getElementById("readyBtn").addEventListener("click", () => {
      if (!game.ready) {
        game.sendMessage("READY");
      }
    });

    document.getElementById("quitBtn").addEventListener("click", async () => {
      game.quitting = true;
      await game.leaveMatch();
      window.location.href = "lobby.html";
    });

    window.addEventListener("keydown", (e) => {
      game.keyMove(e.key, 1);
    });

    window.addEventListener("beforeunload", () => {
      if (!game.quitting) {
        game.leaveMatchOnUnload();
      }
    });

  }
});
