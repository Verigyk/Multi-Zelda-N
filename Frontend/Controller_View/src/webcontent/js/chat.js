window.onload = function() {
    const chat = createChatWidget({
        logId: "log",
        inputId: "msg",
        sendButtonId: "send",
        statusId: null
    });

    const id = crypto.randomUUID();
    chat.connect({
        senderId: id,
        displayName: id.substring(0, 8)
    });
}
