const express = require('express');
const http = require('http');
const path = require('path');
const WebSocket = require('ws');

const app = express();
const server = http.createServer(app);

// 1. Serve static frontend dashboard screens
app.use(express.static(path.join(__dirname, 'frontend-angular', 'dist')));
app.use(express.json());

// Redirect root to dashboard index.html
app.get('/', (req, res) => {
    res.redirect('/index.html');
});

// 2. Mock Spring Boot REST endpoints
app.get('/api/v1/sprints/:sprintId/health', (req, res) => {
    console.log(`[REST Gateway] GET Sprint Health: ${req.params.sprintId}`);
    res.json({
        sprintId: req.params.sprintId,
        confidenceScore: 94.2,
        velocityTrend: 42,
        blockerCount: 3,
        status: "ACTIVE"
    });
});

app.post('/api/v1/sprints/approvals/:actionItemId/execute', (req, res) => {
    console.log(`[REST Gateway] POST Approval Action: ${req.params.actionItemId}`);
    res.json({
        actionItemId: req.params.actionItemId,
        status: "EXECUTED",
        message: "Successfully synchronized blocker updates to Azure DevOps Boards."
    });
});

// 3. Mock Python WebSocket Voice Standup Server
const wss = new WebSocket.Server({ port: 8001 });
console.log('[WebSocket Voice DSM] Mock Server listening on port 8001');

wss.on('connection', (ws) => {
    console.log('[WebSocket Voice DSM] WebRTC Audio bridge connected');
    
    // Simulate streaming transcript responses
    const simulations = [
        { type: "transcript", text: "Finished the API gateway integration for the legacy modules. All tests are green." },
        { type: "synthesis", text: "Marcus reported completion of Module A. Sentiment slightly neutral, might need confirmation on scalability parameters." },
        { type: "transcript", text: "I'm currently working on the frontend orchestration. I've hit a small snag with the token refresh logic on the mobile client." },
        { type: "risk", text: "Critical blocker identified in 'Token Refresh Logic'. Potential impact on Mobile Sprint Goal." },
        { type: "action", text: "Action Assigned: Marcus to assist Alex with the OAuth2 implementation after the DSM." }
    ];

    let step = 0;
    const interval = setInterval(() => {
        if (step < simulations.length) {
            ws.send(JSON.stringify(simulations[step]));
            console.log(`[WebSocket Voice DSM] Sent event: ${simulations[step].type}`);
            step++;
        } else {
            clearInterval(interval);
        }
    }, 1000);

    ws.on('message', (message) => {
        try {
            const data = JSON.parse(message);
            console.log(`[WebSocket Voice DSM] Received payload from client:`, data);
            
            if (data.type === 'barge-in-detected') {
                console.log(`[WebSocket Voice DSM] Barge-in detected! Halting active speech stream.`);
                ws.send(JSON.stringify({ type: "halt", text: "AI speech output interrupted." }));
            }
        } catch (e) {
            console.error('Failed to parse WebSocket message');
        }
    });

    ws.on('close', () => {
        console.log('[WebSocket Voice DSM] WebRTC Audio bridge disconnected');
        clearInterval(interval);
    });
});

// Start unified server
const PORT = 3000;
server.listen(PORT, () => {
    console.log('\n=============================================================');
    console.log(`🚀 ENTERPRISE AI SCRUM MASTER PLATFORM READY`);
    console.log(`👉 Access Sprint Health Dashboard: http://localhost:${PORT}/index.html`);
    console.log(`👉 Access DSM Live Voice Console:  http://localhost:${PORT}/dsm-console.html`);
    console.log('=============================================================\n');
});
