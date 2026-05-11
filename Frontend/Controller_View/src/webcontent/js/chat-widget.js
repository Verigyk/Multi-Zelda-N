function createChatWidget(options) {
    const logElement = document.getElementById(options.logId);
    const inputElement = document.getElementById(options.inputId);
    const sendButton = document.getElementById(options.sendButtonId);
    const statusElement = document.getElementById(options.statusId);

    let socket = null;
    let connected = false;
    let senderId = options.senderId || crypto.randomUUID();
    let displayName = options.displayName || senderId;

    function setStatus(text) {
        if (statusElement) {
            statusElement.textContent = text;
        }
    }

    function setEnabled(enabled) {
        inputElement.disabled = !enabled;
        sendButton.disabled = !enabled;
    }

    function chatUrl() {
        const protocol = window.location.protocol === "https:" ? "wss" : "ws";
        const context = window.location.pathname.split("/")[1] || "Controller_View";
        return `${protocol}://${window.location.host}/${context}/ws`;
    }

    function appendMessage(message, isMine) {
        const item = document.createElement("div");
        item.classList.add("chat-message", isMine ? "sent" : "received");
        item.textContent = message;
        logElement.appendChild(item);
        logElement.scrollTop = logElement.scrollHeight;
    }

    function connect(nextOptions = {}) {
        senderId = nextOptions.senderId || senderId;
        displayName = nextOptions.displayName || senderId;

        if (socket && (socket.readyState === WebSocket.OPEN || socket.readyState === WebSocket.CONNECTING)) {
            return;
        }

        socket = new WebSocket(chatUrl());
        setEnabled(false);
        setStatus("Chat connection...");

        socket.onopen = () => {
            connected = true;
            setEnabled(true);
            setStatus("Chat connected");
        };

        socket.onmessage = (event) => {
            const data = JSON.parse(event.data);
            const isMine = data.senderId === senderId;
            const name = data.displayName || data.senderId;
            appendMessage(name + ": " + data.text, isMine);
        };

        socket.onerror = () => {
            setStatus("Chat error");
        };

        socket.onclose = () => {
            connected = false;
            setEnabled(false);
            setStatus("Chat disconnected");
        };
    }

    function disconnect() {
        if (socket) {
            socket.close();
            socket = null;
        }
        connected = false;
        setEnabled(false);
    }

    function send() {
        const text = inputElement.value.trim();
        if (!text || !connected) return;

        socket.send(JSON.stringify({
            senderId,
            displayName,
            text
        }));
        inputElement.value = "";
        inputElement.focus();
    }

    sendButton.addEventListener("click", send);
    inputElement.addEventListener("keydown", (event) => {
        if (event.key === "Enter") {
            send();
        }
    });

    setEnabled(false);
    return { connect, disconnect, setEnabled };
}
