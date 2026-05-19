const RESUME_KEY = "zelda-current-matchId";
const game = new Vue({

  el: "#game",
  
  data: {
    connection : null,
    matchId: new URLSearchParams(window.location.search).get("matchId") || localStorage.getItem(RESUME_KEY),
    playerId: null,
    ready: false,
    roomState: "WAITING",
    previousRoomState: "WAITING",
    playerGems: 0,
    remainingSeconds: 180,
    winnerName: "",
    pressedKeys: {},
    movementLoop: null,
    quitting: false,
    notificationTimeout: null,
    scoreboardPlayers: {}
  },

  methods : {
    setKeyState(key, isPressed) {
      const normalizedKey = key.toLowerCase();
      if (!["z", "s", "q", "d"].includes(normalizedKey)) return;
      this.pressedKeys[normalizedKey] = isPressed;
    },

    sendMovementInput() {
      if (this.roomState !== "RUNNING") return;
      if (document.hidden) return;

      const dx = (this.pressedKeys.d ? 1 : 0) - (this.pressedKeys.q ? 1 : 0);
      const dy = (this.pressedKeys.s ? 1 : 0) - (this.pressedKeys.z ? 1 : 0);
      if (dx === 0 && dy === 0) return;

      this.sendMessage(JSON.stringify({
        type: "Move",
        dx,
        dy
      }));
    },

    sendThrowBomb() {
      if (this.roomState !== "RUNNING") return;
      this.sendMessage(JSON.stringify({
        type: "ThrowBomb"
      }));
    },

    sendMessage: function(message) {
      if (this.connection && this.connection.readyState === WebSocket.OPEN) {
        this.connection.send(message);
      }
    },

    updateState: function(data) {
      switch (data["type"]) {
        case "Map":
          game.updateMap(data["data"]);
          break;
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
        case "Notification":
          game.showNotification(data["message"] || "Notification");
          break;
        case "Gems":
          game.updateGems(data["data"]);
          break;
        case "BombSpawns":
          game.updateBombSpawns(data["data"]);
          break;
        case "Projectiles":
          game.updateProjectiles(data["data"]);
          break;
      }
    },

    updateRoomState: function(data) {
      this.previousRoomState = this.roomState;
      this.roomState = data["state"];
      if (this.roomState !== "RUNNING") {
        this.pressedKeys = {};
      }
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

      if (this.roomState === "FINISHED" && this.winnerName === "Annulé") {
        this.showNotification("La partie a été annulée.");
        setTimeout(() => {
          localStorage.removeItem(RESUME_KEY);
          window.location.href = "lobby.html";
        }, 1200);
        return;
      }

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

    updateMap: function(data) {
      const gameElement = document.getElementById("game");
      const obstacleLayer = document.getElementById("obstacleLayer");

      gameElement.style.width = data.width + "px";
      gameElement.style.height = data.height + "px";
      obstacleLayer.innerHTML = "";

      for (const obstacle of data.obstacles || []) {
        const element = document.createElement("div");
        element.setAttribute("class", "obstacle");
        element.style.left = obstacle.x + "px";
        element.style.top = obstacle.y + "px";
        element.style.width = obstacle.width + "px";
        element.style.height = obstacle.height + "px";
        obstacleLayer.appendChild(element);
      }
    },

    updatePositions: function(data) {
      for (const [key, player] of Object.entries(data)) {
        if (!Array.isArray(player)) {
          this.scoreboardPlayers[key] = player;
        }
      }
      this.updateScoreboard();
      for (const [key, player] of Object.entries(data)) {
        const element = this.$el.querySelector(`#s${key}`);

        const top = Array.isArray(player) ? player[1] : player.y;
        const left = Array.isArray(player) ? player[0] : player.x;
        const color = Array.isArray(player) ? "red" : player.color;
        const gems = Array.isArray(player) ? 0 : player.gems;
        const hasBomb = !Array.isArray(player) && player.hasBomb;
        const dead = !Array.isArray(player) && player.dead;
        let playerElement = element;

        if (element === null) {
          const child = document.createElement('div')

          child.setAttribute('id', `s${key}`)
          child.setAttribute('class', 'squareBox')

          child.style.top = top + "px";
          child.style.left = left + "px";
          child.style.backgroundColor = color;

          this.$el.appendChild(child)
          playerElement = child;
        } else {
          element.style.top = top + "px";
          element.style.left = left + "px";
          element.style.backgroundColor = color;
        }

        if (key === this.playerId) {
          this.playerGems = gems;
          document.getElementById("gemCount").textContent = String(this.playerGems);
        }

        playerElement.classList.toggle("dead", dead);
        this.updateCarriedBomb(playerElement, hasBomb);
      }
    },

    updateScoreboard: function() {
      const scoreList = document.getElementById("scoreList");
      if (!scoreList) return;

      const players = Object.values(this.scoreboardPlayers)
        .filter(player => player && !Array.isArray(player))
        .sort((a, b) => (b.gems || 0) - (a.gems || 0));

      scoreList.innerHTML = players.map(player => {
        const color = player.color || "#94a3b8";
        const gems = player.gems || 0;
        return `\n          <li class="scoreRow">\n            <div class="scoreInfo">\n              <span class="scoreDot" style="background:${color};"></span>\n              <span class="scoreName">${this.escapeHtml(player.pseudo || player.id || "Joueur")}</span>\n            </div>\n            <span class="scoreGems">${gems}</span>\n          </li>\n        `;
      }).join("");
    },

    showNotification: function(message) {
      const notification = document.getElementById("notification");
      if (!notification) {
        alert(message);
        return;
      }
      notification.textContent = message;
      notification.style.display = "block";
      clearTimeout(this.notificationTimeout);
      this.notificationTimeout = setTimeout(() => {
        notification.style.display = "none";
      }, 5000);
    },

    escapeHtml: function(unsafe) {
      return String(unsafe)
        .replace(/&/g, "&amp;")
        .replace(/</g, "&lt;")
        .replace(/>/g, "&gt;")
        .replace(/\"/g, "&quot;")
        .replace(/'/g, "&#039;");
    },

    updateCarriedBomb: function(playerElement, hasBomb) {
      if (!playerElement) return;
      let bomb = playerElement.querySelector(".carriedBomb");
      if (hasBomb && bomb === null) {
        bomb = document.createElement("div");
        bomb.setAttribute("class", "carriedBomb");
        playerElement.appendChild(bomb);
      } else if (!hasBomb && bomb !== null) {
        bomb.remove();
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

    updateBombSpawns: function(data) {
      const layer = document.getElementById("bombSpawnLayer");
      const existing = new Set();

      for (const [key, spawn] of Object.entries(data)) {
        existing.add(`bomb-spawn-${key}`);
        let element = layer.querySelector(`#bomb-spawn-${key}`);
        if (element === null) {
          element = document.createElement("div");
          element.setAttribute("id", `bomb-spawn-${key}`);
          layer.appendChild(element);
        }
        element.setAttribute("class", spawn.available ? "bombSpawn available" : "bombSpawn cooling");
        element.style.left = spawn.x + "px";
        element.style.top = spawn.y + "px";
      }

      layer.querySelectorAll(".bombSpawn").forEach((element) => {
        if (!existing.has(element.id)) {
          element.remove();
        }
      });
    },

    updateProjectiles: function(data) {
      const layer = document.getElementById("projectileLayer");
      const existing = new Set();

      for (const [key, projectile] of Object.entries(data)) {
        existing.add(`projectile-${key}`);
        let element = layer.querySelector(`#projectile-${key}`);
        if (element === null) {
          element = document.createElement("div");
          element.setAttribute("id", `projectile-${key}`);
          element.setAttribute("class", "bombProjectile");
          layer.appendChild(element);
        }
        element.style.left = projectile.x + "px";
        element.style.top = projectile.y + "px";
      }

      layer.querySelectorAll(".bombProjectile").forEach((element) => {
        if (!existing.has(element.id)) {
          element.remove();
        }
      });
    },

    removePlayers: function(data) {
      for (const id of data) {
        const element = this.$el.querySelector(`#s${id}`);
        if (element) {
          element.remove();
        }
        if (this.scoreboardPlayers[id]) {
          delete this.scoreboardPlayers[id];
        }
      }
      this.updateScoreboard();
    },

    leaveMatch: async function() {
      try {
        await fetch(`../matches/${encodeURIComponent(this.matchId)}/leave`, {
          method: "POST",
          credentials: "include"
        });
      } catch (e) {
        console.warn("Unable to notify backend of match leave", e);
      } finally {
        if (this.connection) {
          localStorage.removeItem(RESUME_KEY);
          this.connection.close();
        }
      }
    },

    leaveMatchOnUnload: function() {
      const url = `../matches/${encodeURIComponent(this.matchId)}/leave`;
      if (navigator.sendBeacon) {
        navigator.sendBeacon(url, "");
      }
      if (this.connection) {
        this.connection.close();
      }
    },

    stopMovementLoop: function() {
      if (this.movementLoop) {
        clearInterval(this.movementLoop);
        this.movementLoop = null;
      }
    }
  },

  created : function() {
    if (!this.matchId) {
      console.error("Missing matchId in URL");
      return;
    }

    localStorage.setItem(RESUME_KEY, this.matchId);
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
      game.stopMovementLoop();
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

    this.movementLoop = setInterval(() => {
      game.sendMovementInput();
    }, 33);

    window.addEventListener("keydown", (e) => {
      game.setKeyState(e.key, true);
      if (["z", "s", "q", "d"].includes(e.key.toLowerCase())) {
        e.preventDefault();
      }
      if ((e.key === " " || e.key.toLowerCase() === "p") && !e.repeat) {
        game.sendThrowBomb();
        e.preventDefault();
      }
    });

    window.addEventListener("keyup", (e) => {
      game.setKeyState(e.key, false);
      if (["z", "s", "q", "d"].includes(e.key.toLowerCase())) {
        e.preventDefault();
      }
    });

    window.addEventListener("beforeunload", () => {
      if (!game.quitting) {
        game.leaveMatchOnUnload();
      }
    });

  }
});
