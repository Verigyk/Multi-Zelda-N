const API = "http://localhost:8080/facade/matches";
const state = {
    active: [],
    history: [],
    selectedId: null
};

async function api(path, options = {}) {
    const response = await fetch(API + path, {
        headers: { "Content-Type": "application/json" },
        credentials : 'include',
        ...options
    });
    if (!response.ok) {
        throw new Error("HTTP " + response.status);
    }
    if (response.status === 204) return null;
    return response.json();
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
}

async function refreshData() {
    try {
        const [active, history] = await Promise.all([
            api("/active"),
            api("/history")
        ]);
        state.active = active;
        state.history = history;

        const exists = selectedMatch();
        if (!exists && state.active.length > 0) {
            state.selectedId = state.active[0].id;
        }
        renderAll();
    } catch (error) {
        document.getElementById("statusMsg").textContent =
            "Erreur API: " + error.message + " (backend démarré ?)";
    }
}

async function createMatch() {
    const title = document.getElementById("titleInput").value;
    const maxPlayers = Number(document.getElementById("maxPlayersInput").value);
    await api("/create", {
        method: "POST",
        body: JSON.stringify({ title, maxPlayers })
    });
    document.getElementById("titleInput").value = "";
    await refreshData();
}

async function handleAction(action, id) {
    try {
        if (action === "join") {
            await api("/" + id + "/join", { method: "POST" });
        } else if (action === "start") {
            await api("/" + id + "/start", { method: "POST" });
        } else if (action === "finish") {
            const winner = prompt("Nom du vainqueur (optionnel):", "");
            await api("/" + id + "/finish", {
                method: "POST",
                body: JSON.stringify({ winner: winner || "" })
            });
            state.selectedId = id;
        }
        await refreshData();
    } catch (error) {
        document.getElementById("statusMsg").textContent =
            "Action impossible: " + error.message;
    }
}

window.onload = function() {
    console.log(document.getElementById("createBtn"));
    document.getElementById("createBtn").addEventListener("click", createMatch);
    refreshData();
    setInterval(refreshData, 2000);
}
