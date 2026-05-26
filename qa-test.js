const http = require('http');
const WebSocket = require('ws');
const fs = require('fs');
const path = require('path');

// Colors for output
const GREEN = '\x1b[32m';
const RED = '\x1b[31m';
const YELLOW = '\x1b[33m';
const CYAN = '\x1b[36m';
const RESET = '\x1b[0m';

console.log(`${CYAN}=============================================================`);
console.log(`🧪 AUTONOMOUS SYSTEM QA INTEGRATION TESTS`);
console.log(`Target: http://localhost:3000 & ws://localhost:8001`);
console.log(`=============================================================${RESET}\n`);

const testResults = [];

function logTest(name, passed, details = "") {
    if (passed) {
        console.log(`  ${GREEN}✓ [PASS]${RESET} ${name} ${details ? `- ${details}` : ""}`);
    } else {
        console.log(`  ${RED}✗ [FAIL]${RESET} ${name} ${details ? `(${details})` : ""}`);
    }
    testResults.push({ name, passed, details });
}

// Helper to make async HTTP GET requests
function checkEndpoint(urlPath) {
    return new Promise((resolve) => {
        http.get(`http://localhost:3000${urlPath}`, (res) => {
            let data = '';
            res.on('data', (chunk) => data += chunk);
            res.on('end', () => {
                resolve({
                    statusCode: res.statusCode,
                    headers: res.headers,
                    body: data
                });
            });
        }).on('error', (err) => {
            resolve({ statusCode: 0, body: err.message });
        });
    });
}

async function runQA() {
    // -------------------------------------------------------------
    // TEST SUITE 1: Frontend Static Page Verifications (HTTP 200)
    // -------------------------------------------------------------
    console.log(`${YELLOW}⚡ [SUITE 1] Verifying Frontend Dashboards...${RESET}`);
    
    const pages = [
        { path: '/index.html', title: 'AgileIntelligence | AI Scrum Master' },
        { path: '/dsm-console.html', title: 'DSM Live Voice Console | Synthetic Agile' },
        { path: '/planning.html', title: 'Synthetic Agile | AI Sprint Planning Engine' }
    ];

    for (const page of pages) {
        const result = await checkEndpoint(page.path);
        const exists = result.statusCode === 200 && result.body.includes('<title>');
        const matchesTitle = result.body.includes(page.title);
        
        logTest(
            `Frontend Page: ${page.path}`, 
            exists && matchesTitle, 
            `Status: ${result.statusCode}, Matches Title: ${matchesTitle}`
        );
    }
    console.log("");

    // -------------------------------------------------------------
    // TEST SUITE 2: Spring Boot REST API Mock Compliance
    // -------------------------------------------------------------
    console.log(`${YELLOW}⚡ [SUITE 2] Verifying REST API Responses...${RESET}`);

    // Test 1: Get Health metrics
    const healthRes = await checkEndpoint('/api/v1/sprints/Alpha-7/health');
    let healthPassed = false;
    let healthDetails = healthRes.body;
    try {
        const payload = JSON.parse(healthRes.body);
        healthPassed = healthRes.statusCode === 200 && payload.confidenceScore === 94.2;
        healthDetails = `Confidence Score parsed: ${payload.confidenceScore}%`;
    } catch(e) {}
    logTest('REST Endpoint: GET /sprints/:sprintId/health', healthPassed, healthDetails);

    // Test 2: Post Action execution (HITL Approval)
    return new Promise(async (resolveSuite2) => {
        const req = http.request({
            host: 'localhost',
            port: 3000,
            path: '/api/v1/sprints/approvals/ACT-101/execute',
            method: 'POST',
            headers: { 'Content-Type': 'application/json' }
        }, (res) => {
            let data = '';
            res.on('data', (chunk) => data += chunk);
            res.on('end', () => {
                let approvalPassed = false;
                let approvalDetails = data;
                try {
                    const payload = JSON.parse(data);
                    approvalPassed = res.statusCode === 200 && payload.status === 'EXECUTED';
                    approvalDetails = `Mitigation status: ${payload.status}`;
                } catch(e) {}
                logTest('REST Endpoint: POST /sprints/approvals/:id/execute', approvalPassed, approvalDetails);
                console.log("");
                resolveSuite2();
            });
        });
        req.write(JSON.stringify({}));
        req.end();
    }).then(async () => {
        // -------------------------------------------------------------
        // TEST SUITE 3: Real-Time Python WebSocket Voice Channel Stream
        // -------------------------------------------------------------
        console.log(`${YELLOW}⚡ [SUITE 3] Verifying Voice Engine WebSockets (Port 8001)...${RESET}`);
        
        return new Promise((resolveWS) => {
            const ws = new WebSocket('ws://localhost:8001');
            const eventsReceived = [];
            let connected = false;

            ws.on('open', () => {
                connected = true;
                logTest('WebSocket Connect: ws://localhost:8001', true);
            });

            ws.on('message', (message) => {
                try {
                    const data = JSON.parse(message);
                    eventsReceived.push(data.type);
                    
                    if (eventsReceived.length === 5) {
                        // Check that sequence contains key components
                        const hasTranscript = eventsReceived.includes('transcript');
                        const hasSynthesis = eventsReceived.includes('synthesis');
                        const hasRisk = eventsReceived.includes('risk');
                        
                        logTest(
                            'WebSocket Stream Sequencing', 
                            hasTranscript && hasSynthesis && hasRisk,
                            `Streamed events sequence: [${eventsReceived.join(', ')}]`
                        );
                        
                        // Test barge-in interruption push
                        ws.send(JSON.stringify({ type: 'barge-in-detected' }));
                    }

                    if (data.type === 'halt') {
                        logTest('WebSocket Barge-in Event Parsing', true, 'Voice speaker suspended on halt command.');
                        ws.close();
                    }
                } catch(e) {
                    logTest('WebSocket Stream Payload Integrity', false, 'Invalid JSON stream buffer');
                    ws.close();
                }
            });

            ws.on('close', () => {
                resolveWS();
            });

            ws.on('error', (err) => {
                if (!connected) {
                    logTest('WebSocket Connect: ws://localhost:8001', false, err.message);
                }
                resolveWS();
            });

            // Set watchdog timer to ensure test finishes even on failure
            setTimeout(() => {
                if (eventsReceived.length < 5) {
                    logTest('WebSocket Stream Sequencing', false, 'Timeout waiting for stream buffers');
                    ws.close();
                }
            }, 12000);
        });
    }).then(() => {
        // -------------------------------------------------------------
        // PRINT REPORT
        // -------------------------------------------------------------
        console.log(`\n${CYAN}=============================================================`);
        console.log(`📊 FINAL REPORT`);
        console.log(`=============================================================${RESET}`);
        
        const passedCount = testResults.filter(t => t.passed).length;
        const failedCount = testResults.filter(t => !t.passed).length;
        
        console.log(`  Total Tests Checked: ${testResults.length}`);
        console.log(`  Tests Passed:       ${GREEN}${passedCount}${RESET}`);
        console.log(`  Tests Failed:       ${failedCount > 0 ? RED : RESET}${failedCount}${RESET}`);
        
        if (failedCount === 0) {
            console.log(`\n  ${GREEN}🎉 QA PASSED SUCCESSFULLY. ALL OPERATIONAL ENDPOINTS ALIVE AND ALIGNED.${RESET}`);
        } else {
            console.log(`\n  ${RED}⚠ QA ALERTS FOUND. PLEASE REVIEW FAILED PIPELINES.${RESET}`);
        }
        console.log(`${CYAN}=============================================================${RESET}\n`);
    });
}

runQA();
