const myId = crypto.randomUUID();

const log = (s, isMine) => {
    const container = document.getElementById("log");

    const div = document.createElement("div");
    div.classList.add("message", isMine ? "sent" : "received");
    div.textContent = s;

    container.appendChild(div);

    // Scroll automatique vers le bas
    container.scrollTop = container.scrollHeight;
};

const wsUrl = `ws://localhost:8080/Controller_View/ws`;
const ws = new WebSocket(wsUrl);

ws.onopen = () => log("[client] connected to " + wsUrl, true);
ws.onmessage = (e) => {
    const { text, senderId } = JSON.parse(e.data);
    const isMine = senderId === myId;
    log("[" + senderId + "] " + text, isMine);
};
ws.onclose = () => log("[client] disconnected", true);
ws.onerror = () => log("[client] websocket error", true);

function send() {
    const inp = document.getElementById("msg");
    const text = inp.value.trim();
    if (!text) return;
    ws.send(JSON.stringify({ text, senderId: myId }));
    inp.value = "";
    inp.focus();
}

document.getElementById("send").onclick = send;
document.getElementById("msg").addEventListener("keydown", (e) => {
    if (e.key === "Enter") send();
});