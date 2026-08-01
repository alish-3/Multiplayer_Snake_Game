(function() {
    'use strict';

    var canvas = document.getElementById('gameCanvas');
    if (!canvas) {
        console.error('Canvas not found!');
        return;
    }
    var ctx = canvas.getContext('2d');
    var swipeArea = document.getElementById('swipeArea');

    // ---- BUG 1 FIX: room code comes from the URL query param first,
    // with fallback to the #roomCodeDisplay <strong> element. ----
    function getRoomCode() {
        try {
            var code = new URLSearchParams(window.location.search).get('room');
            if (code && code.trim()) return code.trim().toUpperCase();
        } catch (e) {}
        var el = document.getElementById('roomCodeDisplay');
        if (el) {
            var strong = el.querySelector('strong');
            var text = (strong ? strong.textContent : el.textContent).trim();
            text = text.replace(/^Room\s*:/i, '').trim();
            if (text) return text.toUpperCase();
        }
        return '';
    }
    var roomCode = getRoomCode();

    var playerNameInput = document.getElementById('playerName');
    var playerName = playerNameInput ? playerNameInput.value : '';

    var playerColorInput = document.getElementById('playerColor');
    var playerColor = playerColorInput && /^#[0-9a-fA-F]{6}$/.test(playerColorInput.value) ? playerColorInput.value : '#e94560';

    var isGuestInput = document.getElementById('isGuest');
    var isGuest = isGuestInput ? isGuestInput.value === 'true' : false;

    var GRID_SIZE = 30;
    var CELL_SIZE = 20;
    var DPR = window.devicePixelRatio || 1;
    var canvasSize = 600;

    var ws = null;
    var gameRunning = false;
    var gameOver = false;
    var gameStarted = false;
    var countdown = -1;
    var isReady = false;
    var scoreSaved = false;
    var connectionLost = false;

    var prevState = null;
    var nextState = null;
    var interpStart = 0;
    var interpDuration = 120;

    var localDirection = 'RIGHT';
    var serverFoods = [];
    var serverSnakes = null;
    var finalResult = null;  // server final snapshot at game over: { snakes, durationMs }

    var gridCache = null;
    var lastScoreboardHash = '';
    var isMobile = ('ontouchstart' in window) || (navigator.maxTouchPoints > 0);
    var reconnectTimer = null;
    var reconnectAttempts = 0;
    var MAX_RECONNECT = 20;
    var foodPulse = 0;

    var particles = [];
    var audioCtx = null;
    var controlScheme = 'dpad';
    var soundEnabled = true;
    var showGrid = true;
    var particlesEnabled = true;
    var startTime = 0;
    var lastPongAt = 0;
    var pingTimer = null;
    var swipeStartX = 0;
    var swipeStartY = 0;

    // Message queue for when WebSocket is connecting
    var wsMessageQueue = [];
    var wsConnecting = false;

    // BUG 4: HUD row containers (values live in nested spans).
    var gameTimerEl = document.getElementById('gameTimer');
    var currentRankEl = document.getElementById('currentRank');

    function setText(id, text) {
        var el = document.getElementById(id);
        if (el) el.textContent = text;
    }

    // Defense in depth: escape player names before injecting into HTML
    function escHtml(s) {
        return String(s)
            .replace(/&/g, '&amp;')
            .replace(/</g, '&lt;')
            .replace(/>/g, '&gt;')
            .replace(/"/g, '&quot;')
            .replace(/'/g, '&#39;');
    }

    function getBasePath() {
        var path = window.location.pathname;
        var parts = path.split('/').filter(Boolean);
        if (parts.length > 1) {
            return '/' + parts[0];
        }
        return '';
    }

    function getWsUrl() {
        var proto = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
        var port = '8080';
        return proto + '//' + window.location.host + ':' + port + '/api/game/ws/' + encodeURIComponent(roomCode) + '/' + encodeURIComponent(playerName);
    }

    // ---------- settings + UI wiring (BUG 4) ----------

    function loadSettings() {
        try {
            soundEnabled = localStorage.getItem('snake_sound') !== 'false';
            showGrid = localStorage.getItem('snake_grid') !== 'false';
            particlesEnabled = localStorage.getItem('snake_particles') !== 'false';
            var saved = localStorage.getItem('snake_controlScheme');
            if (saved === 'swipe' || saved === 'dpad') controlScheme = saved;
        } catch (e) {}
    }

    function saveSetting(key, value) {
        try { localStorage.setItem(key, value); } catch (e) {}
    }

    function syncControlUI() {
        var ids = ['controlSwipe', 'controlDpad', 'modalControlSwipe', 'modalControlDpad'];
        for (var i = 0; i < ids.length; i++) {
            var el = document.getElementById(ids[i]);
            if (el) el.checked = (el.value === controlScheme);
        }
        var toggle = document.getElementById('controlSchemeToggle');
        if (toggle) toggle.title = controlScheme === 'swipe' ? 'Swipe controls active' : 'D-Pad controls active';
    }

    function updateControlScheme(scheme) {
        if (scheme !== 'swipe' && scheme !== 'dpad') return;
        controlScheme = scheme;
        saveSetting('snake_controlScheme', scheme);
        if (scheme === 'swipe') {
            document.documentElement.classList.add('swipe-mode');
        } else {
            document.documentElement.classList.remove('swipe-mode');
        }
        syncControlUI();
        showToast(scheme === 'swipe' ? 'Swipe controls enabled' : 'D-Pad controls enabled');
        adjustSize();
    }

    function updateSoundUI() {
        setText('soundBtn', soundEnabled ? '🔊' : '🔇');
        setText('mobileSoundBtn', soundEnabled ? '🔊' : '🔇');
        var t = document.getElementById('soundToggle');
        if (t) t.checked = soundEnabled;
    }

    function toggleSound() {
        soundEnabled = !soundEnabled;
        saveSetting('snake_sound', soundEnabled ? 'true' : 'false');
        updateSoundUI();
        showToast(soundEnabled ? 'Sound on' : 'Sound off');
    }

    function syncSettingsUI() {
        syncControlUI();
        updateSoundUI();
        var gt = document.getElementById('showGridToggle');
        if (gt) gt.checked = showGrid;
        var pt = document.getElementById('particlesToggle');
        if (pt) pt.checked = particlesEnabled;
    }

    function openSettings() {
        var m = document.getElementById('settingsModal');
        if (m) m.classList.add('open');
    }

    function closeSettingsModal() {
        var m = document.getElementById('settingsModal');
        if (m) m.classList.remove('open');
    }

    function toggleFullscreen() {
        var doc = document;
        if (!doc.fullscreenElement && !doc.webkitFullscreenElement) {
            var elem = doc.documentElement;
            var p;
            if (elem.requestFullscreen) p = elem.requestFullscreen();
            else if (elem.webkitRequestFullscreen) { elem.webkitRequestFullscreen(); p = Promise.resolve(); }
            else if (elem.msRequestFullscreen) { elem.msRequestFullscreen(); p = Promise.resolve(); }
            if (p && p.catch) p.catch(function() { showToast('Fullscreen unavailable'); });
        } else {
            var p;
            if (doc.exitFullscreen) p = doc.exitFullscreen();
            else if (doc.webkitExitFullscreen) { doc.webkitExitFullscreen(); p = Promise.resolve(); }
            else if (doc.msExitFullscreen) { doc.msExitFullscreen(); p = Promise.resolve(); }
            if (p && p.catch) p.catch(function() {});
        }
    }

    function updateFullscreenIcons() {
        var active = !!(document.fullscreenElement || document.webkitFullscreenElement);
        setText('fullscreenBtn', active ? '🗗' : '⛶');
        setText('mobileFullscreenBtn', active ? '🗗' : '⛶');
    }

    function copyRoomCode() {
        function fallback() {
            var ta = document.createElement('textarea');
            ta.value = roomCode;
            ta.setAttribute('readonly', '');
            ta.style.position = 'fixed';
            ta.style.opacity = '0';
            document.body.appendChild(ta);
            ta.select();
            try { document.execCommand('copy'); } catch (e) {}
            document.body.removeChild(ta);
            showToast('Room code copied: ' + roomCode);
        }
        if (navigator.clipboard && navigator.clipboard.writeText) {
            navigator.clipboard.writeText(roomCode).then(function() {
                showToast('Room code copied: ' + roomCode);
            }).catch(fallback);
        } else {
            fallback();
        }
    }

    function toggleScoresPanel(force) {
        var sb = document.getElementById('sidebar');
        if (!sb) return;
        if (typeof force === 'boolean') {
            sb.classList.toggle('open', force);
        } else {
            sb.classList.toggle('open');
        }
        var arrow = document.getElementById('closeScoresBtn');
        if (arrow) arrow.textContent = sb.classList.contains('open') ? '▼' : '▲';
        adjustSize();
    }

    function wireUi() {
        var el;

        el = document.getElementById('copyRoomBtn');
        if (el) el.addEventListener('click', copyRoomCode);
        el = document.getElementById('fullscreenBtn');
        if (el) el.addEventListener('click', toggleFullscreen);
        el = document.getElementById('mobileFullscreenBtn');
        if (el) el.addEventListener('click', toggleFullscreen);
        el = document.getElementById('soundBtn');
        if (el) el.addEventListener('click', toggleSound);
        el = document.getElementById('mobileSoundBtn');
        if (el) el.addEventListener('click', toggleSound);
        el = document.getElementById('settingsBtn');
        if (el) el.addEventListener('click', openSettings);
        el = document.getElementById('mobileSettingsBtn');
        if (el) el.addEventListener('click', openSettings);
        el = document.getElementById('closeSettings');
        if (el) el.addEventListener('click', closeSettingsModal);
        el = document.getElementById('closeScoresBtn');
        if (el) el.addEventListener('click', function() { toggleScoresPanel(); });

        // Tapping the visible header strip pulls the panel back up
        var sb = document.getElementById('sidebar');
        if (sb) {
            var sbHeader = sb.querySelector('.sidebar-header');
            if (sbHeader) sbHeader.addEventListener('click', function(e) {
                if (!sb.classList.contains('open') && !(e.target && e.target.closest && e.target.closest('#closeScoresBtn'))) {
                    toggleScoresPanel(true);
                }
            });
        }

        // Overlay click closes the settings modal
        var modal = document.getElementById('settingsModal');
        if (modal) {
            modal.addEventListener('click', function(e) {
                if (e.target === modal) closeSettingsModal();
            });
        }

        document.addEventListener('fullscreenchange', updateFullscreenIcons);
        document.addEventListener('webkitfullscreenchange', updateFullscreenIcons);

        // Control scheme radios (sidebar + modal)
        var radioIds = ['controlSwipe', 'controlDpad', 'modalControlSwipe', 'modalControlDpad'];
        for (var i = 0; i < radioIds.length; i++) {
            var r = document.getElementById(radioIds[i]);
            if (r) r.addEventListener('change', function() { updateControlScheme(this.value); });
        }

        var soundToggle = document.getElementById('soundToggle');
        if (soundToggle) soundToggle.addEventListener('change', function() {
            soundEnabled = this.checked;
            saveSetting('snake_sound', soundEnabled ? 'true' : 'false');
            updateSoundUI();
        });

        var gridToggle = document.getElementById('showGridToggle');
        if (gridToggle) gridToggle.addEventListener('change', function() {
            showGrid = this.checked;
            saveSetting('snake_grid', showGrid ? 'true' : 'false');
            gridCache = null; // force redraw without grid lines
        });

        var particlesToggle = document.getElementById('particlesToggle');
        if (particlesToggle) particlesToggle.addEventListener('change', function() {
            particlesEnabled = this.checked;
            saveSetting('snake_particles', particlesEnabled ? 'true' : 'false');
        });

        var clearBtn = document.getElementById('clearDataBtn');
        if (clearBtn) clearBtn.addEventListener('click', function() {
            try {
                localStorage.removeItem('snake_token');
                localStorage.removeItem('snake_controlScheme');
                localStorage.removeItem('snake_sound');
                localStorage.removeItem('snake_grid');
                localStorage.removeItem('snake_particles');
            } catch (e) {}
            soundEnabled = true;
            showGrid = true;
            particlesEnabled = true;
            controlScheme = 'dpad';
            document.documentElement.classList.remove('swipe-mode');
            gridCache = null;
            syncSettingsUI();
            showToast('Local data cleared');
        });
    }

    function init() {
        loadSettings();
        if (controlScheme === 'swipe') {
            document.documentElement.classList.add('swipe-mode');
        } else {
            document.documentElement.classList.remove('swipe-mode');
        }
        syncSettingsUI();
        wireUi();

        Ajax.post('api/game', {
            action: 'join',
            roomCode: roomCode,
            playerName: playerName,
            color: playerColor
        }, function(data) {
            if (data && data.success) {
                gameRunning = true;
                connectWs();
                startPingLoop();
                requestAnimationFrame(gameLoop);
            } else {
                alert('Failed to join: ' + ((data && data.error) || 'Unknown'));
            }
        });

        document.addEventListener('keydown', handleKeyDown);
        
        // Initialize audio on first user interaction (click OR keydown)
        var audioInitDone = false;
        function initAudioOnce() {
            if (!audioInitDone) {
                audioInitDone = true;
                initAudio();
                document.removeEventListener('click', initAudioOnce);
                document.removeEventListener('keydown', initAudioOnce);
            }
        }
        document.addEventListener('click', initAudioOnce, { once: true });
        document.addEventListener('keydown', initAudioOnce, { once: true });

        var readyBtn = document.getElementById('touchReadyBtn');
        if (readyBtn) {
            readyBtn.addEventListener('click', sendReady);
            readyBtn.addEventListener('touchstart', function(e) {
                e.preventDefault();
                sendReady();
            }, { passive: false });
        }
        document.addEventListener('touchmove', function(e) {
            if (e.target === canvas || (canvas && canvas.contains(e.target)) || e.target === swipeArea) {
                e.preventDefault();
            }
        }, { passive: false });

        setupControls();

        canvas.addEventListener('click', function() {
            if (gameOver || !gameStarted) sendReady();
        });
        window.addEventListener('resize', adjustSize);
        window.addEventListener('orientationchange', function() {
            setTimeout(adjustSize, 100);
        });
        adjustSize();

        requestFullscreenIfMobile();
    }

    // ---------- WebSocket ----------

    function connectWs() {
        if (ws) {
            try { ws.close(); } catch(e) {}
            ws = null;
        }
        wsConnecting = true;
        var url = getWsUrl();
        console.log('Connecting to WebSocket:', url);

        ws = new WebSocket(url);
        ws.binaryType = 'blob';

        ws.onopen = function() {
            console.log('WebSocket connected');
            connectionLost = false;
            reconnectAttempts = 0;
            wsConnecting = false;
            if (reconnectTimer) {
                clearTimeout(reconnectTimer);
                reconnectTimer = null;
            }
            // Flush queued messages
            while (wsMessageQueue.length > 0) {
                var msg = wsMessageQueue.shift();
                ws.send(JSON.stringify(msg));
            }
        };

        ws.onmessage = function(e) {
            try {
                var data = JSON.parse(e.data);
                if (data && data.action === 'pong') {
                    lastPongAt = Date.now();
                    var latency = Date.now() - (data.t || lastPongAt);
                    var txt = latency < 10 ? '<10ms' : latency + 'ms';
                    setText('pingVal', txt);
                    setText('mobilePingVal', txt);
                    return;
                }
                onServerState(data);
            } catch(err) {
                console.error('WS message parse error:', err);
            }
        };

        ws.onclose = function(e) {
            console.log('WebSocket closed:', e.code, e.reason);
            ws = null;
            wsConnecting = false;
            wsMessageQueue.length = 0; // Clear queue on close
            connectionLost = true;
            if (gameRunning) scheduleReconnect();
        };

        ws.onerror = function(e) {
            console.error('WebSocket error:', e);
            ws = null;
            wsConnecting = false;
            wsMessageQueue.length = 0; // Clear queue on error
            connectionLost = true;
        };
    }

    function scheduleReconnect() {
        if (reconnectAttempts >= MAX_RECONNECT) return;
        reconnectAttempts++;
        var delay = Math.min(1000 * Math.pow(1.5, reconnectAttempts - 1), 15000);
        reconnectTimer = setTimeout(function() {
            if (gameRunning) connectWs();
        }, delay);
    }

    function startPingLoop() {
        if (pingTimer) return;
        pingTimer = setInterval(function() {
            if (ws && ws.readyState === WebSocket.OPEN) {
                sendToServer({ action: 'ping', t: Date.now() });
                if (Date.now() - lastPongAt > 10000) {
                    setText('pingVal', '-');
                    setText('mobilePingVal', '-');
                }
            }
        }, 5000);
    }

    function sendToServer(msg) {
        if (ws && ws.readyState === WebSocket.OPEN) {
            ws.send(JSON.stringify(msg));
        } else if (wsConnecting) {
            // Queue message to send when connection opens
            wsMessageQueue.push(msg);
        }
    }

    // ---------- game actions ----------

    function sendReady() {
        if (isReady && !gameOver && gameStarted) return;
        isReady = true;
        sendToServer({ action: 'ready' });
        Ajax.post('api/game', {
            action: 'ready', roomCode: roomCode, playerName: playerName
        });
    }

    function sendDirection(dir) {
        if (gameOver || !gameStarted) return;
        var opposite = { 'UP': 'DOWN', 'DOWN': 'UP', 'LEFT': 'RIGHT', 'RIGHT': 'LEFT' };
        if (opposite[dir] === localDirection) return;

        localDirection = dir;

        sendToServer({ action: 'move', direction: dir });
    }

    function handleKeyDown(e) {
        if (e.key === 'Escape') {
            closeSettingsModal();
            return;
        }
        if (e.repeat) return;
        var key = e.key;
        if (key === ' ' || key === 'Space' || key === 'Spacebar') {
            e.preventDefault();
            sendReady();
            return;
        }
        var keyMap = {
            'ArrowUp': 'UP', 'ArrowDown': 'DOWN',
            'ArrowLeft': 'LEFT', 'ArrowRight': 'RIGHT',
            'w': 'UP', 's': 'DOWN', 'a': 'LEFT', 'd': 'RIGHT',
            'W': 'UP', 'S': 'DOWN', 'A': 'LEFT', 'D': 'RIGHT'
        };
        var dir = keyMap[key];
        if (!dir) return;
        e.preventDefault();
        sendDirection(dir);
    }

    // ---------- touch controls (swipe + dpad) ----------

    function handleDpadTouch(e) {
        e.preventDefault();
        e.stopPropagation();
        if (!gameStarted || gameOver) return;
        sendDirection(this.dataset.dir);
    }

    function handleDpadMouse(e) {
        e.preventDefault();
        if (!gameStarted || gameOver) return;
        sendDirection(this.dataset.dir);
    }

    // BUG 5 fix: swipe gestures bind to #swipeArea (only visible in swipe mode),
    // so the D-Pad and swipe area never fight over the canvas.
    function setupSwipe() {
        var area = document.getElementById('swipeArea');
        if (!area || area._swipeBound) return;
        area._swipeBound = true;
        var startX = 0, startY = 0;
        area.addEventListener('touchstart', function(e) {
            e.preventDefault();
            var t = e.touches[0];
            startX = t.clientX;
            startY = t.clientY;
        }, { passive: false });
        area.addEventListener('touchmove', function(e) {
            e.preventDefault();
        }, { passive: false });
        area.addEventListener('touchend', function(e) {
            e.preventDefault();
            if (!gameStarted || gameOver) {
                sendReady();
                startX = 0;
                startY = 0;
                return;
            }
            if (startX === 0 && startY === 0) return;
            var t = e.changedTouches[0];
            var dx = t.clientX - startX;
            var dy = t.clientY - startY;
            var absDx = Math.abs(dx);
            var absDy = Math.abs(dy);
            if (Math.max(absDx, absDy) < 15) return;
            if (absDx > absDy) {
                sendDirection(dx > 0 ? 'RIGHT' : 'LEFT');
            } else {
                sendDirection(dy > 0 ? 'DOWN' : 'UP');
            }
            startX = 0;
            startY = 0;
        }, { passive: false });
        area.addEventListener('click', function() {
            if (gameOver || !gameStarted) sendReady();
        });
    }

    function setupDpad() {
        var buttons = document.querySelectorAll('.touch-dpad .dpad-btn');
        for (var i = 0; i < buttons.length; i++) {
            if (buttons[i]._dpadBound) continue;
            buttons[i]._dpadBound = true;
            buttons[i].addEventListener('touchstart', handleDpadTouch, { passive: false });
            buttons[i].addEventListener('mousedown', handleDpadMouse);
        }
    }

    function setupControls() {
        setupSwipe();
        setupDpad();
    }

    function requestFullscreenIfMobile() {
        if (!isMobile) return;
        if (window.innerWidth <= window.innerHeight) return;
        var elem = document.documentElement;
        var p;
        if (elem.requestFullscreen) p = elem.requestFullscreen();
        else if (elem.webkitRequestFullscreen) { elem.webkitRequestFullscreen(); p = Promise.resolve(); }
        else if (elem.msRequestFullscreen) { elem.msRequestFullscreen(); p = Promise.resolve(); }
        if (p && p.catch) p.catch(function(err) {
            console.log('Fullscreen request failed:', err);
        });
    }

    // ---------- state handling ----------

    function findMySnake(snakes) {
        if (!snakes) return null;
        for (var i = 0; i < snakes.length; i++) {
            if (snakes[i].name === playerName) return snakes[i];
        }
        return null;
    }

    function saveScore(score) {
        if (isGuest || score <= 0 || scoreSaved) return;
        scoreSaved = true;
        Ajax.post('api/auth', {
            action: 'saveScore', username: playerName, score: score
        });
    }

    function extractPositions(snakes) {
        var result = {};
        if (!snakes) return result;
        for (var i = 0; i < snakes.length; i++) {
            var s = snakes[i];
            var segs = s.segments || [];
            var list = [];
            for (var j = 0; j < segs.length; j++) {
                list.push({ x: segs[j].x, y: segs[j].y });
            }
            result[s.name] = {
                color: s.color || '#e94560',
                segments: list,
                alive: s.alive,
                score: s.score || 0,
                direction: s.direction || 'RIGHT'
            };
        }
        return result;
    }

    // HUD updates: player count, alive count, my score, my rank
    function updateHud(snakes) {
        var list = snakes || [];
        setText('playerCountVal', list.length);

        var alive = 0;
        for (var i = 0; i < list.length; i++) {
            if (list[i].alive) alive++;
        }
        setText('playersAliveVal', alive);

        var my = findMySnake(list);
        setText('mobileScoreVal', my ? (my.score || 0) : 0);

        var sorted = list.slice().sort(function(a, b) {
            var d = (b.score || 0) - (a.score || 0);
            if (d !== 0) return d;
            return a.name < b.name ? -1 : (a.name > b.name ? 1 : 0);
        });
        var rank = 0;
        for (var i = 0; i < sorted.length; i++) {
            if (sorted[i].name === playerName) { rank = i + 1; break; }
        }
        if (currentRankEl) {
            setText('rankVal', rank > 0 ? '#' + rank : '-');
            setText('mobileRank', rank > 0 ? 'Rank #' + rank : 'Rank #-');
        }
    }

    function onServerState(data) {
        if (!data || data.action === 'pong') return;
        var now = performance.now();

        if (interpStart > 0) {
            interpDuration = Math.min(now - interpStart, 200);
        }

        var oldNext = nextState;
        var newPositions = extractPositions(data.snakes);

        if (oldNext) {
            var same = true;
            var keys = Object.keys(newPositions);
            var oldKeys = Object.keys(oldNext);
            if (keys.length !== oldKeys.length) same = false;
            else {
                for (var k = 0; k < keys.length && same; k++) {
                    var name = keys[k];
                    var a = oldNext[name], b = newPositions[name];
                    if (!a || !b || a.alive !== b.alive || a.score !== b.score) {
                        same = false;
                    } else {
                        var sa = a.segments, sb = b.segments;
                        if (sa.length !== sb.length) same = false;
                        else {
                            for (var s = 0; s < sa.length && same; s++) {
                                if (sa[s].x !== sb[s].x || sa[s].y !== sb[s].y) same = false;
                            }
                        }
                    }
                }
            }
            if (!same) {
                prevState = nextState;
                nextState = newPositions;
                interpStart = now;
            }
        } else {
            prevState = newPositions;
            nextState = newPositions;
            interpStart = now;
        }

        connectionLost = false;

        var prevFoods = serverFoods;
        var foodsData = data.foods;
        if (!foodsData && data.food) {
            foodsData = [data.food];
        }
        serverFoods = foodsData ? foodsData.map(function(f) { return { x: f.x, y: f.y, type: f.type || 'NORMAL' }; }) : [];
        countdown = data.countdown;
        if (countdown === 0) countdown = -1;

        if (data.gameOver) {
            // Server final snapshot for the game-over overlay; interpolation is
            // cleared so the overlay can never merge stale frames.
            finalResult = { snakes: data.snakes || null, durationMs: data.roundDurationMs || 0 };
            prevState = null;
            nextState = null;
            interpStart = 0;
            gameOver = true;
            gameStarted = false;
            isReady = false;
            if (!isGuest && !scoreSaved) {
                var my = findMySnake(data.snakes);
                if (my) saveScore(my.score || 0);
            }
            if (audioCtx) playBeep(200, 0.5, 'sawtooth', 0.06);
        } else {
            // New round / countdown / waiting payload: drop the previous
            // game-over snapshot and clear any leftover round timer.
            finalResult = null;
            if (data.countdown >= 0) {
                setText('timerVal', '0:00');
                startTime = 0;
            }
            if (data.gameStarted) {
                if (!gameStarted) {
                    startTime = Date.now(); // timer starts on round start (BUG 4: timer)
                    if (audioCtx) playBeep(660, 0.1, 'square', 0.06);
                }
                gameOver = false;
                gameStarted = true;
            }
        }

        if (data.snakes) {
            for (var i = 0; i < data.snakes.length; i++) {
                if (data.snakes[i].name === playerName) {
                    isReady = data.snakes[i].ready === true;
                    break;
                }
            }
            for (var i = 0; i < data.snakes.length; i++) {
                var s = data.snakes[i];
                if (!s.alive && prevState && prevState[s.name] && prevState[s.name].alive) {
                    var head = s.segments && s.segments[0];
                    if (head) spawnParticles(head.x, head.y, '#e94560', 15, 80);
                    if (audioCtx) playBeep(300, 0.25, 'sawtooth', 0.05);
                }
            }
        }

        if (data.snakes) {
            serverSnakes = data.snakes;
            updateHud(data.snakes);
        }

        if (prevFoods && serverFoods) {
            var prevFoodKey = prevFoods.map(function(f) { return f.x + ',' + f.y; }).join(';');
            var currFoodKey = serverFoods.map(function(f) { return f.x + ',' + f.y; }).join(';');
            if (prevFoodKey !== currFoodKey) {
                for (var i = 0; i < serverFoods.length; i++) {
                    var food = serverFoods[i];
                    var isNew = true;
                    for (var j = 0; j < prevFoods.length; j++) {
                        if (prevFoods[j].x === food.x && prevFoods[j].y === food.y) {
                            isNew = false;
                            break;
                        }
                    }
                    if (isNew) {
                        spawnParticles(food.x, food.y, food.type === 'GOLDEN' ? '#ffd700' : '#ff4444', 10, 50);
                        if (audioCtx) playBeep(food.type === 'GOLDEN' ? 880 : 660, 0.08, 'square', 0.05);
                    }
                }
            }
        }
    }

    function getInterpPositions(t) {
        t = Math.min(Math.max(t, 0), 1);
        var result = {};
        if (!nextState) return result;
        var allNames = Object.keys(nextState);
        for (var n = 0; n < allNames.length; n++) {
            var name = allNames[n];
            var next = nextState[name];
            var prev = prevState ? prevState[name] : null;
            if (!next) continue;
            var nextSegs = next.segments;
            var prevSegs = prev ? prev.segments : null;
            var interpSegs = [];
            var len = nextSegs.length;

            if (prevSegs && prevSegs.length === len) {
                for (var i = 0; i < len; i++) {
                    interpSegs.push({
                        x: prevSegs[i].x + (nextSegs[i].x - prevSegs[i].x) * t,
                        y: prevSegs[i].y + (nextSegs[i].y - prevSegs[i].y) * t
                    });
                }
            } else if (prevSegs && prevSegs.length < len) {
                for (var i = 0; i < len; i++) {
                    if (i < prevSegs.length) {
                        interpSegs.push({
                            x: prevSegs[i].x + (nextSegs[i].x - prevSegs[i].x) * t,
                            y: prevSegs[i].y + (nextSegs[i].y - prevSegs[i].y) * t
                        });
                    } else {
                        interpSegs.push({ x: nextSegs[i].x, y: nextSegs[i].y });
                    }
                }
            } else {
                for (var i = 0; i < len; i++) {
                    interpSegs.push({ x: nextSegs[i].x, y: nextSegs[i].y });
                }
            }

            result[name] = {
                color: next.color,
                segments: interpSegs,
                alive: next.alive,
                score: next.score,
                direction: next.direction
            };
        }
        return result;
    }

    // ---------- render loop ----------

    function gameLoop(time) {
        if (!gameRunning) return;
        render(time);
        requestAnimationFrame(gameLoop);
    }

    function formatTime(ms) {
        var s = Math.max(0, Math.floor((ms || 0) / 1000));
        var mins = Math.floor(s / 60);
        var secs = s % 60;
        return mins + ':' + (secs < 10 ? '0' : '') + secs;
    }

    function render(time) {
        var dpr = window.devicePixelRatio || 1;
        ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
        var w = canvasSize;
        var h = canvasSize;
        var elapsed = interpStart ? time - interpStart : 0;
        var t = Math.min(elapsed / interpDuration, 1);
        var interpSnakes = getInterpPositions(t);

        foodPulse = (time / 300) % 1;

        // Round timer (BUG 4: timer) — freezes automatically once gameOver is set
        if (gameStarted && !gameOver && startTime > 0 && gameTimerEl) {
            var ms = Date.now() - startTime;
            var mins = Math.floor(ms / 60000);
            var secs = Math.floor((ms % 60000) / 1000);
            setText('timerVal', mins + ':' + (secs < 10 ? '0' : '') + secs);
        }

        ctx.clearRect(0, 0, w, h);
        drawGrid();

        if (connectionLost) {
            ctx.fillStyle = 'rgba(233, 69, 96, 0.15)';
            ctx.fillRect(0, 0, w, 4);
            ctx.fillStyle = '#e94560';
            ctx.font = '11px sans-serif';
            ctx.textAlign = 'left';
            ctx.textBaseline = 'top';
            ctx.fillText('Reconnecting...', 6, 6);
        }

        if (!serverSnakes) {
            ctx.fillStyle = '#555';
            ctx.font = '20px sans-serif';
            ctx.textAlign = 'center';
            ctx.textBaseline = 'middle';
            ctx.fillText('Connecting...', w / 2, h / 2);
            updateScoreboard(null);
            return;
        }

        if (countdown <= 0) {
            var names = Object.keys(interpSnakes);
            for (var n = 0; n < names.length; n++) {
                var name = names[n];
                var isLocal = name === playerName;
                var snake = interpSnakes[name];
                var color = snake.color;
                var segs = snake.segments;
                var alive = snake.alive;

                if (!alive && segs.length > 0) {
                    ctx.globalAlpha = 0.3;
                }

                var segLen = segs.length;

                var dir = 'RIGHT';
                if (isLocal && gameStarted && !gameOver) {
                    dir = localDirection;
                } else {
                    var ns = nextState ? nextState[name] : null;
                    if (ns && ns.segments && ns.segments.length > 1) {
                        var hx = ns.segments[0].x, hy = ns.segments[0].y;
                        var bx = ns.segments[1].x, by = ns.segments[1].y;
                        if (hx > bx) dir = 'RIGHT';
                        else if (hx < bx) dir = 'LEFT';
                        else if (hy > by) dir = 'DOWN';
                        else if (hy < by) dir = 'UP';
                    }
                }

                for (var i = segLen - 1; i >= 0; i--) {
                    var seg = segs[i];
                    var x = seg.x * CELL_SIZE;
                    var y = seg.y * CELL_SIZE;

                    if (i === 0) {
                        var cx = x + CELL_SIZE / 2;
                        var cy = y + CELL_SIZE / 2;
                        var r = CELL_SIZE / 2 - 1;

                        ctx.fillStyle = isLocal ? '#fff' : color;
                        ctx.shadowColor = color;
                        ctx.shadowBlur = 6;
                        ctx.beginPath();
                        ctx.arc(cx, cy, r, 0, Math.PI * 2);
                        ctx.fill();
                        ctx.shadowBlur = 0;

                        ctx.fillStyle = '#000';
                        var es = 2.5, eo = 3.5;
                        if (dir === 'RIGHT') {
                            ctx.fillRect(cx + eo, cy - es * 2 - 0.5, es, es);
                            ctx.fillRect(cx + eo, cy + es - 0.5, es, es);
                        } else if (dir === 'LEFT') {
                            ctx.fillRect(cx - eo - es, cy - es * 2 - 0.5, es, es);
                            ctx.fillRect(cx - eo - es, cy + es - 0.5, es, es);
                        } else if (dir === 'UP') {
                            ctx.fillRect(cx - es * 2 - 0.5, cy - eo - es, es, es);
                            ctx.fillRect(cx + es - 0.5, cy - eo - es, es, es);
                        } else if (dir === 'DOWN') {
                            ctx.fillRect(cx - es * 2 - 0.5, cy + eo, es, es);
                            ctx.fillRect(cx + es - 0.5, cy + eo, es, es);
                        }
                    } else {
                        var pad = 1;
                        var alpha = 1 - (i / segLen) * 0.5;
                        ctx.fillStyle = hexToRgba(color, alpha);
                        var s = CELL_SIZE - pad * 2;
                        var rx = x + pad, ry = y + pad;
                        var radius = Math.min(3, s / 2);
                        ctx.beginPath();
                        ctx.moveTo(rx + radius, ry);
                        ctx.lineTo(rx + s - radius, ry);
                        ctx.quadraticCurveTo(rx + s, ry, rx + s, ry + radius);
                        ctx.lineTo(rx + s, ry + s - radius);
                        ctx.quadraticCurveTo(rx + s, ry + s, rx + s - radius, ry + s);
                        ctx.lineTo(rx + radius, ry + s);
                        ctx.quadraticCurveTo(rx, ry + s, rx, ry + s - radius);
                        ctx.lineTo(rx, ry + radius);
                        ctx.quadraticCurveTo(rx, ry, rx + radius, ry);
                        ctx.closePath();
                        ctx.fill();
                    }
                }

                if (!alive && segs.length > 0) {
                    ctx.globalAlpha = 1;
                }
            }
        }

        if (serverFoods && serverFoods.length > 0) {
            var pulse = 1 + 0.08 * Math.sin(foodPulse * Math.PI * 2);
            for (var fi = 0; fi < serverFoods.length; fi++) {
                var food = serverFoods[fi];
                var fx = food.x * CELL_SIZE + CELL_SIZE / 2;
                var fy = food.y * CELL_SIZE + CELL_SIZE / 2;
                var fr = (CELL_SIZE / 2 - 2) * pulse;
                var fColor = (food.type === 'GOLDEN') ? '#ffd700' : '#ff4444';
                var fGlow = (food.type === 'GOLDEN') ? '#ffd700' : '#ff4444';
                if (food.type === 'GOLDEN') {
                    fr *= 1.15;
                }
                ctx.fillStyle = fColor;
                ctx.shadowColor = fGlow;
                ctx.shadowBlur = food.type === 'GOLDEN' ? 16 : 8;
                ctx.beginPath();
                ctx.arc(fx, fy, fr, 0, Math.PI * 2);
                ctx.fill();
                if (food.type === 'GOLDEN') {
                    ctx.fillStyle = 'rgba(255,215,0,0.3)';
                    ctx.beginPath();
                    ctx.arc(fx, fy, fr * 1.4, 0, Math.PI * 2);
                    ctx.fill();
                }
                ctx.shadowBlur = 0;
            }
        }

        updateParticles();
        drawParticles();
        updateScoreboard(serverSnakes);

        if (countdown > 0) {
            ctx.fillStyle = 'rgba(0,0,0,0.4)';
            ctx.fillRect(0, 0, w, h);
            ctx.fillStyle = '#fff';
            ctx.font = 'bold 72px sans-serif';
            ctx.textAlign = 'center';
            ctx.textBaseline = 'middle';
            ctx.shadowColor = '#e94560';
            ctx.shadowBlur = 25;
            var scale = 1 + (1 - t) * 0.15;
            ctx.setTransform(scale, 0, 0, scale, w / 2 * (1 - scale), h / 2 * (1 - scale));
            ctx.fillText(countdown, w / 2, h / 2);
            ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
            ctx.shadowBlur = 0;
        } else if (gameOver) {
            ctx.fillStyle = 'rgba(0,0,0,0.65)';
            ctx.fillRect(0, 0, w, h);
            var snapshotSnakes = finalResult && finalResult.snakes ? finalResult.snakes : serverSnakes;
            var sorted = snapshotSnakes ? snapshotSnakes.slice().sort(function(a, b) { return (b.score || 0) - (a.score || 0); }) : [];
            var my = snapshotSnakes ? findMySnake(snapshotSnakes) : null;

            ctx.fillStyle = '#ffd700';
            ctx.font = 'bold 28px sans-serif';
            ctx.textAlign = 'center';
            ctx.textBaseline = 'middle';
            ctx.fillText('GAME OVER', w / 2, h / 2 - 80);

            ctx.fillStyle = '#aaa';
            ctx.font = '14px sans-serif';
            ctx.fillText('Time: ' + formatTime(finalResult ? finalResult.durationMs : 0), w / 2, h / 2 - 104);

            var rankY = h / 2 - 46;
            for (var ri = 0; ri < Math.min(sorted.length, 4); ri++) {
                var pl = sorted[ri];
                var medal = ri === 0 ? '\u{1F947}' : ri === 1 ? '\u{1F948}' : ri === 2 ? '\u{1F949}' : '';
                var isLocal = pl.name === playerName;
                ctx.fillStyle = isLocal ? '#16a34a' : '#ddd';
                ctx.font = (ri === 0 ? 'bold 17px' : '15px') + ' sans-serif';
                ctx.fillText(medal + ' ' + pl.name + ' - ' + (pl.score || 0), w / 2, rankY);
                rankY += 24;
            }

            rankY += 6;
            ctx.fillStyle = '#aaa';
            ctx.font = '15px sans-serif';
            ctx.fillText(isMobile ? 'Tap READY to restart' : 'Press SPACE to ready up', w / 2, rankY);
            rankY += 24;
            if (isReady) {
                ctx.fillStyle = '#16a34a';
                ctx.fillText('Ready! Waiting for others...', w / 2, rankY);
                rankY += 24;
            }
        } else if (!gameStarted) {
            ctx.fillStyle = 'rgba(0,0,0,0.5)';
            ctx.fillRect(0, 0, w, h);
            ctx.fillStyle = '#3b82f6';
            ctx.font = 'bold 24px sans-serif';
            ctx.textAlign = 'center';
            ctx.textBaseline = 'middle';
            var total = serverSnakes ? serverSnakes.length : 0;
            var needed = Math.max(0, 2 - total);
            ctx.fillText('Waiting for players...', w / 2, h / 2 - 30);
            ctx.fillStyle = '#a8a8b3';
            ctx.font = '15px sans-serif';
            ctx.fillText(total + ' player' + (total !== 1 ? 's' : '') + ' in room' + (needed > 0 ? ' (' + needed + ' more needed)' : ''), w / 2, h / 2 + 4);
            ctx.fillStyle = '#ddd';
            ctx.font = '16px sans-serif';
            ctx.fillText(isMobile ? 'Tap READY when ready' : 'Press SPACE when ready', w / 2, h / 2 + 38);
            // Single status line: text and color swap with ready state (never two lines)
            ctx.font = '15px sans-serif';
            if (isReady) {
                ctx.fillStyle = '#16a34a';
                ctx.fillText('Ready! Waiting for others...', w / 2, h / 2 + 72);
            } else {
                ctx.fillStyle = '#a8a8b3';
                ctx.fillText('Not ready yet', w / 2, h / 2 + 72);
            }
        }
    }

    function drawGrid() {
        var dpr = window.devicePixelRatio || 1;
        if (gridCache && gridCache.width === canvas.width && gridCache.height === canvas.height) {
            ctx.drawImage(gridCache, 0, 0);
            return;
        }
        var physCellSize = CELL_SIZE * dpr;
        gridCache = document.createElement('canvas');
        gridCache.width = canvas.width;
        gridCache.height = canvas.height;
        var gctx = gridCache.getContext('2d');
        // BUG 4: "Show Grid" setting — background still fills, lines skipped
        if (showGrid) {
            gctx.strokeStyle = '#1a1a3e';
            gctx.lineWidth = 1;
            for (var i = 0; i <= GRID_SIZE; i++) {
                gctx.beginPath();
                gctx.moveTo(i * physCellSize, 0);
                gctx.lineTo(i * physCellSize, canvas.height);
                gctx.stroke();
                gctx.beginPath();
                gctx.moveTo(0, i * physCellSize);
                gctx.lineTo(canvas.width, i * physCellSize);
                gctx.stroke();
            }
        }
        ctx.drawImage(gridCache, 0, 0);
    }

    // BUG 4: escape player names before innerHTML injection (defense in depth)
    function updateScoreboard(snakes) {
        var sb = document.getElementById('scoreboard');
        if (!sb) return;
        var hash = '';
        if (snakes) {
            var sorted = snakes.slice().sort(function(a, b) { return (b.score || 0) - (a.score || 0); });
            for (var i = 0; i < sorted.length; i++) {
                var s = sorted[i];
                hash += s.name + ':' + (s.score || 0) + ':' + s.alive + ':' + s.ready + '|';
            }
        }
        if (hash === lastScoreboardHash) return;
        lastScoreboardHash = hash;

        if (!snakes || snakes.length === 0) {
            sb.innerHTML = '<p class="empty-msg">Waiting for players...</p>';
            return;
        }
        var sorted = snakes.slice().sort(function(a, b) { return (b.score || 0) - (a.score || 0); });
        var html = '';
        var rankEmoji = ['\u{1F947}', '\u{1F948}', '\u{1F949}'];
        var rankClass = ['gold', 'silver', 'bronze'];
        for (var i = 0; i < sorted.length; i++) {
            var snake = sorted[i];
            var isLocal = snake.name === playerName;
            var cls = isLocal ? 'score-item local-player' : 'score-item';
            if (!snake.alive) cls += ' dead';
            var status = snake.alive ? '' : ' \u2620';
            var readyStatus = snake.ready ? ' \u2705' : '';
            var rankBadge = '';
            if (i < 3) {
                rankBadge = '<span class="rank-badge ' + rankClass[i] + '">' + rankEmoji[i] + '</span>';
            }
            html += '<div class="' + cls + '">' +
                '<span class="player-name">' + rankBadge + (isLocal ? '\u2B50 ' : '') + escHtml(snake.name) + status + readyStatus + '</span>' +
                '<span class="player-score">' + (snake.score || 0) + '</span></div>';
        }
        sb.innerHTML = html;
    }

    function hexToRgba(hex, alpha) {
        var r = parseInt(hex.slice(1, 3), 16);
        var g = parseInt(hex.slice(3, 5), 16);
        var b = parseInt(hex.slice(5, 7), 16);
        return 'rgba(' + r + ',' + g + ',' + b + ',' + alpha + ')';
    }

    // ---------- audio ----------

    function initAudio() {
        try {
            audioCtx = new (window.AudioContext || window.webkitAudioContext)();
            if (audioCtx.state === 'suspended') {
                audioCtx.resume();
            }
        } catch (e) {}
    }

    // BUG 4: sound setting — muted players produce no beeps at all
    function playBeep(freq, duration, type, volume) {
        if (!audioCtx || !soundEnabled) return;
        try {
            var osc = audioCtx.createOscillator();
            var gain = audioCtx.createGain();
            osc.type = type || 'square';
            osc.frequency.value = freq;
            gain.gain.value = volume || 0.08;
            gain.gain.exponentialRampToValueAtTime(0.001, audioCtx.currentTime + duration);
            osc.connect(gain);
            gain.connect(audioCtx.destination);
            osc.start();
            osc.stop(audioCtx.currentTime + duration);
        } catch (e) {}
    }

    // ---------- particles ----------

    // BUG 4: particles setting — no-op when disabled
    function spawnParticles(x, y, color, count, speed) {
        if (!particlesEnabled) return;
        for (var i = 0; i < (count || 8); i++) {
            var angle = Math.random() * Math.PI * 2;
            var spd = (Math.random() * 2 + 1) * (speed || 60);
            particles.push({
                x: x * CELL_SIZE + CELL_SIZE / 2,
                y: y * CELL_SIZE + CELL_SIZE / 2,
                vx: Math.cos(angle) * spd,
                vy: Math.sin(angle) * spd,
                life: 1,
                decay: 0.015 + Math.random() * 0.02,
                color: color || '#ff4444',
                size: 2 + Math.random() * 3
            });
        }
    }

    function updateParticles() {
        for (var i = particles.length - 1; i >= 0; i--) {
            var p = particles[i];
            p.x += p.vx * (1/60);
            p.y += p.vy * (1/60);
            p.vx *= 0.95;
            p.vy *= 0.95;
            p.life -= p.decay;
            if (p.life <= 0) {
                particles.splice(i, 1);
            }
        }
    }

    function drawParticles() {
        for (var i = 0; i < particles.length; i++) {
            var p = particles[i];
            ctx.globalAlpha = p.life;
            ctx.fillStyle = p.color;
            ctx.fillRect(p.x - p.size/2, p.y - p.size/2, p.size, p.size);
        }
        ctx.globalAlpha = 1;
    }

    // ---------- sizing ----------

    function adjustSize() {
        DPR = window.devicePixelRatio || 1;
        var vw = window.innerWidth;
        var vh = window.innerHeight;
        var narrow = vw <= 1024;
        var isLandscape = vw > vh;
        var availW, availH;
        var portraitMobile = narrow && !isLandscape;
        var sb = document.getElementById('sidebar');
        var panelOpen = !!sb && sb.classList.contains('open');

        // Measure the actual touch-controls height (READY + D-Pad) so the
        // leaderboard sits right above it in both dpad and swipe modes
        var controlsH = 240;
        var tc = document.querySelector('.touch-controls');
        if (tc) {
            var tcH = Math.round(tc.getBoundingClientRect().height);
            if (tcH > 0) controlsH = tcH;
        }
        document.documentElement.style.setProperty('--controls-h', controlsH + 'px');

        if (portraitMobile) {
            // portrait: canvas = min(vw - margins, vh - header - bottom reserve)
            var stripSpace = 56; // minimized strip 48px + 8px gap
            var openPanelTarget = Math.max(120, vh * 0.28);
            var bottomReserve = controlsH + (panelOpen ? openPanelTarget : stripSpace);
            availW = vw - 24;
            availH = vh - 48 - bottomReserve - 8;
        } else if (narrow && isLandscape) {
            // landscape: canvas = min(vh - header - dpad - margins, vw - sidebar - margins)
            availW = vw - 190 - 24;
            availH = vh - 48 - 150 - 16;
        } else {
            // desktop: min(vw - sidebar(260) - margins, vh - header - space, 600)
            availW = vw - 260 - 64;
            availH = vh - 160;
        }
        var size = Math.min(availW, availH, 600);
        size = Math.max(size, narrow ? 150 : 240);
        canvasSize = size;
        canvas.style.width = size + 'px';
        canvas.style.height = size + 'px';
        canvas.width = Math.floor(size * DPR);
        canvas.height = Math.floor(size * DPR);
        CELL_SIZE = size / GRID_SIZE;
        canvas.style.touchAction = 'none';
        gridCache = null;

        // Maximized leaderboard reaches the bottom of the game box
        if (portraitMobile && sb) {
            var canvasRect = canvas.getBoundingClientRect();
            var panelBottom = vh - controlsH;
            if (panelOpen) {
                sb.style.height = Math.max(48, panelBottom - canvasRect.bottom + 4) + 'px';
                sb.style.maxHeight = 'none';
            } else {
                sb.style.height = '';
                sb.style.maxHeight = '';
            }
        }
    }

    // ---------- toast ----------

    function showToast(msg, bgColor) {
        var existing = document.querySelector('.game-toast');
        if (existing) existing.remove();
        var toast = document.createElement('div');
        toast.className = 'game-toast';
        toast.textContent = msg;
        if (bgColor) toast.style.background = bgColor;
        document.body.appendChild(toast);
        setTimeout(function() { if (toast.parentNode) toast.parentNode.removeChild(toast); }, 2500);
    }

    // ---------- lifecycle ----------

    document.addEventListener('visibilitychange', function() {
        if (document.hidden) {
            if (ws) { try { ws.close(); } catch(e) {} ws = null; }
        } else if (!ws && gameRunning) {
            connectWs();
        }
    });

    window.addEventListener('beforeunload', function() {
        if (ws) { try { ws.close(); } catch(e) {} }
    });

    window.addEventListener('popstate', function() {
        if (ws) { try { ws.close(); } catch(e) {} }
    });

    init();
})();
