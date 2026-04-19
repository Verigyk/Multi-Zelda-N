
const BASE_URL = "http://localhost:8080/facade/auth";

function showTab(tab) {
    document.getElementById("form-login").style.display   = tab === "login"    ? "block" : "none";
    document.getElementById("form-register").style.display = tab === "register" ? "block" : "none";
    document.querySelectorAll(".tab").forEach(btn => btn.classList.remove("active"));
    event.target.classList.add("active");
    hideMessage();
}

function showMessage(text, type) {
    const el = document.getElementById("message");
    el.textContent = text || (type === "error" ? "Erreur inconnue." : "OK");
    el.className = type;  // "success" ou "error"
    el.style.display = "block";
}

function hideMessage() {
    document.getElementById("message").style.display = "none";
}

async function login() {
    const pseudo   = document.getElementById("login-pseudo").value.trim();
    const password = document.getElementById("login-password").value;

    if (!pseudo || !password) {
        showMessage("Remplis tous les champs.", "error");
        return;
    }

    try {
        const response = await fetch(BASE_URL + "/login", {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ "username" : pseudo,
                                   "password" : password })
        });

        const text = await response.text();

        if (response.ok) {
            showMessage("Bienvenue, " + pseudo + " ! Redirection...", "success");
            // Redirige vers le jeu dans 1.5 secondes
            setTimeout(() => {
                window.location.href = "game.html?pseudo=" + encodeURIComponent(pseudo);
            }, 1500);
        } else {
            showMessage(text || ("Erreur HTTP " + response.status), "error");
        }
    } catch (e) {
        showMessage("Impossible de contacter le serveur.", "error");
    }
}

async function register() {
    const pseudo   = document.getElementById("register-pseudo").value.trim();
    const password = document.getElementById("register-password").value;

    if (!pseudo || !password) {
        showMessage("Remplis tous les champs.", "error");
        return;
    }

    if (pseudo.length < 3) {
        showMessage("Le pseudo doit faire au moins 3 caractères.", "error");
        return;
    }

    try {
        const response = await fetch(BASE_URL + "/addAccount", {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ "pseudo" : pseudo,
                                   "password" : password })
        });

        const text = await response.text();

        if (response.ok) {
            showMessage("Compte créé ! Tu peux maintenant te connecter.", "success");
            setTimeout(() => showTab_name("login"), 1500);
        } else {
            showMessage(text || ("Erreur HTTP " + response.status), "error");
        }
    } catch (e) {
        showMessage("Impossible de contacter le serveur.", "error");
    }
}

function showTab_name(tab) {
    document.getElementById("form-login").style.display    = tab === "login"    ? "block" : "none";
    document.getElementById("form-register").style.display = tab === "register" ? "block" : "none";
    document.querySelectorAll(".tab").forEach((btn, i) => {
        btn.classList.toggle("active", (i === 0 && tab === "login") || (i === 1 && tab === "register"));
    });
    hideMessage();
}

// Connexion avec la touche Entrée
document.addEventListener("keydown", (e) => {
    if (e.key !== "Enter") return;
    if (document.getElementById("form-login").style.display !== "none") login();
    else register();
});
