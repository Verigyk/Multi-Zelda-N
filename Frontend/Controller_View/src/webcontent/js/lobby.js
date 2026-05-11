const state = {
    active: [],
    history: [],
    selectedId: null,
    authenticated: false,
    pseudo: null
};

const API_BASE = "http://localhost:8080/facade";
let ws = null;
let reconnectTimer = null;

function status(text) {
    document.getElementById("statusMsg").textContent = text;
}

function wsUrl() {
    const protocol = window.location.protocol === "https:" ? "wss" : "ws";
    const context = window.location.pathname.split("/")[1] || "Controller_View";
    return `${protocol}://${window.location.host}/${context}/matches/ws`;
}

function connectWs() {
    if (!state.authenticated) {
        return;
    }

    if (ws && (ws.readyState === WebSocket.OPEN || ws.readyState === WebSocket.CONNECTING)) {
        return;
    }

    const url = wsUrl();
    status("Connexion WS: " + url);
    ws = new WebSocket(url);

    ws.onopen = () => {
        status("WebSocket connecté");
        sendAction({ action: "refresh" });
    };

    ws.onmessage = (event) => {
        try {
            const msg = JSON.parse(event.data);
            if (msg.type === "snapshot") {
                state.active = Array.isArray(msg.active) ? msg.active : [];
                state.history = Array.isArray(msg.history) ? msg.history : [];

                const exists = selectedMatch();
                if (!exists) {
                    if (state.active.length > 0) {
                        state.selectedId = state.active[0].id;
                    } else if (state.history.length > 0) {
                        state.selectedId = state.history[0].id;
                    } else {
                        state.selectedId = null;
                    }
                }

                renderAll();
                return;
            }

            if (msg.type === "error") {
                status("Erreur: " + msg.message);
            }
        } catch (error) {
            status("Message WS invalide");
        }
    };

    ws.onerror = () => {
        status("Erreur WebSocket");
    };

    ws.onclose = () => {
        if (!state.authenticated) {
            return;
        }
        status("WebSocket déconnecté, reconnexion...");
        clearTimeout(reconnectTimer);
        reconnectTimer = setTimeout(connectWs, 1500);
    };
}

function sendAction(payload) {
    if (!state.authenticated) {
        status("Connecte-toi pour utiliser le lobby.");
        return;
    }

    if (!ws || ws.readyState !== WebSocket.OPEN) {
        status("WebSocket non connecté");
        return;
    }
    ws.send(JSON.stringify(payload));
}

function formatDate(value) {
    if (!value) return "-";
    return new Date(value).toLocaleString();
}

function matchClass(status) {
    if (status === "LOADING") return "status-loading";
    if (status === "RUNNING") return "status-running";
    if (status === "FINISHED") return "status-finished";
    return "";
}

function selectedMatch() {
    return state.active.find(m => m.id === state.selectedId)
        || state.history.find(m => m.id === state.selectedId)
        || null;
}

function selectedActiveMatch() {
    return state.active.find(m => m.id === state.selectedId) || null;
}

function updateConnectButton() {
    const btn = document.getElementById("connectBtn");
    if (!btn) return;

    const match = selectedActiveMatch();
    btn.disabled = !state.authenticated || !match;
    btn.textContent = match ? "Join game: " + match.title : "Join game";
}

function updateAuthBox() {
    const label = document.getElementById("authLabel");
    const loginBtn = document.getElementById("loginBtn");
    const logoutBtn = document.getElementById("logoutBtn");
    const createBtn = document.getElementById("createBtn");

    if (state.authenticated) {
        label.textContent = "Connected as " + state.pseudo;
        loginBtn.style.display = "none";
        logoutBtn.style.display = "inline-block";
        createBtn.disabled = false;
    } else {
        label.textContent = "Not connected";
        loginBtn.style.display = "inline-block";
        logoutBtn.style.display = "none";
        createBtn.disabled = true;
    }

    updateConnectButton();
}

function renderScreen() {
    const screen = document.getElementById("screen");
    const match = selectedMatch();
    if (!match) {
        screen.innerHTML = `
            <div>
                <h3>Aucune partie sélectionnée</h3>
                <p class="small">Clique sur une partie active ou historique.</p>
            </div>`;
        return;
    }

    if (match.state === "LOADING") {
        screen.innerHTML = `
            <div>
                <h3>Chargement de ${match.title}</h3>
                <p class="small">Partie ${match.id} • ${match.playersCount}/${match.maxPlayers} joueurs</p>
                <div style="margin-top:12px;">
                    <span class="loading-dot"></span>
                    <span class="loading-dot"></span>
                    <span class="loading-dot"></span>
                </div>
            </div>`;
        return;
    }

    if (match.state === "RUNNING") {
        screen.innerHTML = `
            <div>
                <h3>Partie en cours</h3>
                <p>${match.title}</p>
                <p class="small">Démarrée à: ${formatDate(match.startedAt)}</p>
            </div>`;
        return;
    }

    screen.innerHTML = `
        <div>
            <h3>Écran de fin</h3>
            <p>Partie: ${match.title}</p>
            <p>Vainqueur: <strong>${match.winner || "Inconnu"}</strong></p>
            <p class="small">Terminée à: ${formatDate(match.endedAt)}</p>
        </div>`;
}

function renderList(containerId, matches, withActions) {
    const container = document.getElementById(containerId);
    if (matches.length === 0) {
        container.innerHTML = `<div class="small">Aucune donnée.</div>`;
        return;
    }

    container.innerHTML = matches.map(match => `
        <div class="match-card ${state.selectedId === match.id ? "selected" : ""}" data-id="${match.id}">
            <div class="match-head">
                <strong>${match.title}</strong>
                <span class="${matchClass(match.state)}">${match.state}</span>
            </div>
            <div class="small">ID: ${match.id}</div>
            <div class="small">Joueurs: ${match.playersCount}/${match.maxPlayers}</div>
            <div class="small">Créée: ${formatDate(match.createdAt)}</div>
            ${match.startedAt ? `<div class="small">Start: ${formatDate(match.startedAt)}</div>` : ""}
            ${match.endedAt ? `<div class="small">End: ${formatDate(match.endedAt)}</div>` : ""}
            ${match.winner ? `<div class="small">Winner: ${match.winner}</div>` : ""}
            ${withActions ? `
                <div class="actions">
                    <button class="btn-ok" data-action="join" data-id="${match.id}">Join</button>
                    <button class="btn-warn" data-action="start" data-id="${match.id}">Start</button>
                    <button class="btn-danger" data-action="finish" data-id="${match.id}">Finish</button>
                </div>
            ` : ""}
        </div>
    `).join("");

    container.querySelectorAll(".match-card").forEach(card => {
        card.addEventListener("click", (e) => {
            if (e.target.closest("button")) return;
            state.selectedId = card.dataset.id;
            renderAll();
        });
    });

    if (withActions) {
        container.querySelectorAll("button[data-action]").forEach(btn => {
            btn.addEventListener("click", (e) => {
                e.stopPropagation();
                handleAction(btn.dataset.action, btn.dataset.id);
            });
        });
    }
}

function renderAll() {
    renderList("activeList", state.active, true);
    renderList("historyList", state.history, false);
    renderScreen();
    updateConnectButton();
}

function createMatch() {
    if (!state.authenticated) {
        status("Connecte-toi avant de creer une partie.");
        return;
    }

    const title = document.getElementById("titleInput").value;
    const maxPlayers = Number(document.getElementById("maxPlayersInput").value);
    sendAction({ action: "create", title, maxPlayers });
    document.getElementById("titleInput").value = "";
}

function handleAction(action, id) {
    if (action === "join") {
        sendAction({ action: "join", id });
    } else if (action === "start") {
        sendAction({ action: "start", id });
    } else if (action === "finish") {
        const winner = prompt("Nom du vainqueur (optionnel):", "");
        sendAction({ action: "finish", id, winner: winner || "" });
        state.selectedId = id;
    }
}

function connectToGame() {
    if (!state.authenticated) {
        goToLogin();
        return;
    }

    const match = selectedActiveMatch();
    if (!match) {
        status("Selectionne une partie active avant de te connecter.");
        return;
    }

    sendAction({ action: "join", id: match.id });

    const params = new URLSearchParams(window.location.search);
    params.set("matchId", match.id);
    window.location.href = "movingSquare.html?" + params.toString();
}

function goToLogin() {
    window.location.href = "login.html";
}

async function logout() {
    try {
        await fetch(API_BASE + "/auth/logout", {
            method: "POST",
            credentials: "include"
        });
    } finally {
        state.authenticated = false;
        state.pseudo = null;
        state.active = [];
        state.history = [];
        state.selectedId = null;
        clearTimeout(reconnectTimer);
        if (ws) {
            ws.close();
            ws = null;
        }
        status("Disconnected");
        renderAll();
        updateAuthBox();
    }
}

async function checkAuth() {
    try {
        const response = await fetch(API_BASE + "/api/me", {
            credentials: "include"
        });

        if (!response.ok) {
            throw new Error("Not authenticated");
        }

        const user = await response.json();
        state.authenticated = true;
        state.pseudo = user.pseudo;
        updateAuthBox();
        connectWs();
    } catch (error) {
        state.authenticated = false;
        state.pseudo = null;
        status("Connecte-toi pour voir et rejoindre les parties.");
        renderAll();
        updateAuthBox();
    }
}

window.onload = function() {
    document.getElementById("createBtn").addEventListener("click", createMatch);
    document.getElementById("connectBtn").addEventListener("click", connectToGame);
    document.getElementById("loginBtn").addEventListener("click", goToLogin);
    document.getElementById("logoutBtn").addEventListener("click", logout);
    renderAll();
    updateAuthBox();
    checkAuth();
}
