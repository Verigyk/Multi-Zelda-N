const state = {
    active: [],
    history: [],
    selectedId: null,
    authenticated: false,
    pseudo: null
};

const AUTH_BASE = "../auth";
const MATCHES_API_BASE = "../matches";
const RESUME_KEY = "zelda-current-matchId";
let MAPS = [];
let ws = null;
let reconnectTimer = null;
let lobbyChat = null;

function status(text) {
    const element = document.getElementById("statusMsg");
    if (element) {
        element.textContent = text;
    }
}

function getStoredMatchId() {
    return localStorage.getItem(RESUME_KEY);
}

function setStoredMatchId(matchId) {
    if (matchId) {
        localStorage.setItem(RESUME_KEY, matchId);
    } else {
        localStorage.removeItem(RESUME_KEY);
    }
}

function wsUrl() {
    const protocol = window.location.protocol === "https:" ? "wss" : "ws";
    const context = window.location.pathname.split("/")[1] || "Controller_View";
    return `${protocol}://${window.location.host}/${context}/matches/ws`;
}

function resumeIfNeeded() {
    const storedId = getStoredMatchId();
    if (storedId && state.authenticated) {
        goToGame(storedId);
    }
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
                sendAction({ action: "refresh" });
                return;
            }

            if (msg.type === "actionResult" && msg.action === "join") {
                const matchId = msg.match && msg.match.id;
                if (matchId) {
                    goToGame(matchId);
                }
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
        return false;
    }

    if (!ws || ws.readyState !== WebSocket.OPEN) {
        status("WebSocket non connecté");
        return false;
    }
    ws.send(JSON.stringify(payload));
    return true;
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
}

function renderList(containerId, matches, withActions) {
    const container = document.getElementById(containerId);
    if (matches.length === 0) {
        container.innerHTML = `<div class="small">Aucune donnée.</div>`;
        return;
    }

    container.innerHTML = matches.map(match => {
        const joinedButDisconnected = match.joined && !match.hasGameSession;
        const canJoin = match.state === "LOADING" && (joinedButDisconnected || !match.joined);
        return `
        <div class="match-card ${state.selectedId === match.id ? "selected" : ""}" data-id="${match.id}">
            <div class="match-head">
                <strong>${match.title}</strong>
                <span class="${matchClass(match.state)}">${match.state}</span>
            </div>
            <div class="small">ID: ${match.id}</div>
            <div class="small">Map: ${match.mapName || "classic"}</div>
            <div class="small">Joueurs: ${match.playersCount}/${match.maxPlayers}</div>
            <div class="small">Créée: ${formatDate(match.createdAt)}</div>
            ${match.startedAt ? `<div class="small">Start: ${formatDate(match.startedAt)}</div>` : ""}
            ${match.endedAt ? `<div class="small">End: ${formatDate(match.endedAt)}</div>` : ""}
            ${match.winner ? `<div class="small">Winner: ${match.winner}</div>` : ""}
            ${withActions ? `
                <div class="actions">
                    <button class="btn-ok" data-action="join" data-id="${match.id}" ${!canJoin ? "disabled" : ""}>
                        ${joinLabel(match)}
                    </button>
                    ${match.state === "LOADING" && match.createdBy === state.pseudo ? `
                        <button class="btn-danger" data-action="cancel" data-id="${match.id}">Annuler</button>
                    ` : ""}
                </div>
            ` : ""}
        </div>
    `;} ).join("");

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

function joinLabel(match) {
    if (match.joined && match.hasGameSession) return "Already joined";
    if (match.state !== "LOADING") return "Started";
    return "Join";
}

function renderAll() {
    renderList("activeList", state.active, true);
    renderList("historyList", state.history, false);
}

function createMatch() {
    if (!state.authenticated) {
        status("Connecte-toi avant de creer une partie.");
        return;
    }

    const title = document.getElementById("titleInput").value;
    const mapName = document.getElementById("mapSelect").value;
    const maxPlayers = Number(document.getElementById("maxPlayersInput").value);
    sendAction({ action: "create", title, maxPlayers, mapName });
    document.getElementById("titleInput").value = "";
}

async function loadMapsFromBackend() {
    try {
        const response = await fetch(`${MATCHES_API_BASE}/maps`, {
            credentials: "include"
        });
        if (!response.ok) {
            throw new Error("HTTP " + response.status);
        }
        const data = await response.json();
        MAPS = Array.isArray(data)
            ? data.map(item => ({
                name: item.name,
                label: item.name.charAt(0).toUpperCase() + item.name.slice(1),
                maxPlayers: item.maxPlayers
            }))
            : [];
    } catch (error) {
        console.error("Impossible de charger les maps depuis le backend:", error);
        MAPS = [
            { name: "classic", label: "Classic", maxPlayers: 4 },
            { name: "duel", label: "Duel", maxPlayers: 2 },
            { name: "crossroads", label: "Crossroads", maxPlayers: 4 }
        ];
    }
}

function initMapSelector() {
    const mapSelect = document.getElementById("mapSelect");
    const maxPlayersInput = document.getElementById("maxPlayersInput");

    const renderMaps = () => {
        if (!MAPS.length) {
            mapSelect.innerHTML = `<option value="classic">Classic (4 joueurs max)</option>`;
            MAPS = [{ name: "classic", label: "Classic", maxPlayers: 4 }];
        } else {
            mapSelect.innerHTML = MAPS.map(map => `
                <option value="${map.name}" data-max-players="${map.maxPlayers}">
                    ${map.label} (${map.maxPlayers} joueurs max)
                </option>
            `).join("");
        }
    };

    const updateMaxPlayersLimit = () => {
        const selected = MAPS.find(map => map.name === mapSelect.value) || MAPS[0];
        maxPlayersInput.max = String(selected.maxPlayers);
        if (Number(maxPlayersInput.value) > selected.maxPlayers) {
            maxPlayersInput.value = String(selected.maxPlayers);
        }
    };

    mapSelect.addEventListener("change", updateMaxPlayersLimit);
    loadMapsFromBackend().then(() => {
        renderMaps();
        updateMaxPlayersLimit();
    }).catch(() => {
        renderMaps();
        updateMaxPlayersLimit();
    });
}

function handleAction(action, id) {
    if (action === "join") {
        const match = state.active.find(m => m.id === id);
        const sessionActive = match && match.hasGameSession === true;
        if (!match || match.state !== "LOADING" || sessionActive) {
            return;
        }
        status("Connexion à la partie...");
        sendAction({ action: "join", id });
    } else if (action === "cancel") {
        if (!confirm("Annuler la partie et renvoyer tous les joueurs vers le lobby ?")) {
            return;
        }
        sendAction({ action: "cancel", id });
        state.selectedId = id;
    } else if (action === "finish") {
        const winner = prompt("Nom du vainqueur (optionnel):", "");
        sendAction({ action: "finish", id, winner: winner || "" });
        state.selectedId = id;
    }
}

function goToGame(matchId) {
    setStoredMatchId(matchId);
    const params = new URLSearchParams(window.location.search);
    params.set("matchId", matchId);
    window.location.href = "movingSquare.html?" + params.toString();
}

function goToLogin() {
    window.location.href = "login.html";
}

async function logout() {
    try {
        await fetch(AUTH_BASE + "/logout", {
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
        if (lobbyChat) {
            lobbyChat.disconnect();
        }
        status("Disconnected");
        renderAll();
        updateAuthBox();
        window.location.href = "login.html";
    }
}

async function checkAuth() {
    try {
        const response = await fetch(AUTH_BASE + "/me", {
            credentials: "include"
        });

        if (!response.ok) {
            window.location.href = "login.html";
            return;
        }

        const user = await response.json();
        state.authenticated = true;
        state.pseudo = user.pseudo;
        updateAuthBox();
        resumeIfNeeded();
        connectWs();
        lobbyChat.connect({
            senderId: user.pseudo,
            displayName: user.pseudo
        });
    } catch (error) {
        window.location.href = "login.html";
        return;
    }
}

function initLobby() {
    lobbyChat = createChatWidget({
        logId: "chatLog",
        inputId: "chatInput",
        sendButtonId: "chatSendBtn",
        statusId: null
    });

    initMapSelector();
    document.getElementById("createBtn").addEventListener("click", createMatch);
    document.getElementById("loginBtn").addEventListener("click", goToLogin);
    document.getElementById("logoutBtn").addEventListener("click", logout);
    
    renderAll();
    updateAuthBox();
    checkAuth();
}

document.addEventListener("DOMContentLoaded", initLobby);
