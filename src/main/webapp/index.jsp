<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0, user-scalable=no">
    <meta name="theme-color" content="#0d1021">
    <title>Snake Game - Lobby</title>
    <link rel="icon" href="data:image/svg+xml,<svg xmlns=%22http://www.w3.org/2000/svg%22 viewBox=%220 0 100 100%22><text y=%22.9em%22 font-size=%2290%22>🐍</text></svg>">
    <link rel="stylesheet" href="css/style.css?v=4">
</head>
<body class="lobby-page">
    <div class="lobby-container" id="app">
        <h1 class="game-title">🐍 Multiplayer Snake</h1>

        <!-- AUTH SCREEN -->
        <div class="auth-card" id="authScreen">
            <div class="auth-tabs" role="tablist">
                <button class="auth-tab active" data-tab="guest" role="tab">Guest</button>
                <button class="auth-tab" data-tab="login" role="tab">Login</button>
                <button class="auth-tab" data-tab="register" role="tab">Register</button>
            </div>

            <!-- GUEST -->
            <div class="auth-panel active" id="panelGuest">
                <p class="auth-desc">Play without an account. Your scores and progress will not be saved.</p>
                <div class="auth-notice">⚠️ Your data will not be saved as a guest!</div>
                <button class="btn btn-primary" id="guestBtn">Play as Guest</button>
            </div>

            <!-- LOGIN -->
            <div class="auth-panel" id="panelLogin">
                <div class="form-group">
                    <label for="loginUsername">Username</label>
                    <input type="text" id="loginUsername" placeholder="Enter username" maxlength="50" autocomplete="username">
                </div>
                <div class="form-group">
                    <label for="loginPassword">Password</label>
                    <input type="password" id="loginPassword" placeholder="Enter password" maxlength="100" autocomplete="current-password">
                </div>
                <div class="auth-error" id="loginError"></div>
                <label class="remember-label">
                    <input type="checkbox" id="rememberMe"> Remember me (30 days)
                </label>
                <button class="btn btn-primary" id="loginBtn">Login</button>
                <p class="auth-switch">Don't have an account? <a href="#" data-switch="register">Register here</a></p>
            </div>

            <!-- REGISTER -->
            <div class="auth-panel" id="panelRegister">
                <div class="form-group">
                    <label for="regUsername">Username</label>
                    <input type="text" id="regUsername" placeholder="Choose a username" maxlength="50" autocomplete="username">
                </div>
                <div class="form-group">
                    <label for="regPassword">Password</label>
                    <input type="password" id="regPassword" placeholder="Choose a password" maxlength="100" autocomplete="new-password">
                </div>
                <div class="form-group">
                    <label for="regConfirm">Confirm Password</label>
                    <input type="password" id="regConfirm" placeholder="Confirm password" maxlength="100" autocomplete="new-password">
                </div>
                <div class="auth-error" id="regError"></div>
                <button class="btn btn-primary" id="regBtn">Create Account</button>
                <p class="auth-switch">Already have an account? <a href="#" data-switch="login">Login here</a></p>
            </div>
        </div>

        <!-- LOBBY SCREEN -->
        <div class="lobby-card" id="lobby" style="display:none;">
            <div class="lobby-user" id="lobbyUser"></div>
            <div class="form-group">
                <label for="playerName">Your Name</label>
                <input type="text" id="playerName" placeholder="Enter your name..." maxlength="20" autocomplete="off">
            </div>

            <div class="form-group">
                <label>Your Snake Color</label>
                <div class="color-options" id="colorOptions">
                    <div class="color-option selected" style="background:#e94560" data-color="#e94560"></div>
                    <div class="color-option" style="background:#16a34a" data-color="#16a34a"></div>
                    <div class="color-option" style="background:#3b82f6" data-color="#3b82f6"></div>
                    <div class="color-option" style="background:#facc15" data-color="#facc15"></div>
                    <div class="color-option" style="background:#a855f7" data-color="#a855f7"></div>
                    <div class="color-option" style="background:#f97316" data-color="#f97316"></div>
                    <div class="color-option" style="background:#06b6d4" data-color="#06b6d4"></div>
                    <div class="color-option" style="background:#ec4899" data-color="#ec4899"></div>
                </div>
            </div>

            <!-- Game Mode Selection - Card Style -->
            <div class="form-group" id="gameModeGroup">
                <div class="mode-selector">
                    <h4>Choose How to Play</h4>
                    <div class="mode-cards">
                        <button type="button" class="mode-card friends active" data-mode="friends" role="tab" aria-selected="true">
                            <span class="mode-card-glow"></span>
                            <span class="mode-card-icon-wrap">
                                <span class="mode-card-icon">👥</span>
                                <span class="mode-card-icon-pulse"></span>
                            </span>
                            <span class="mode-card-title">Play with Friends</span>
                            <span class="mode-card-desc">Create or join rooms with other players. Share room codes and play together!</span>
                            <div class="mode-card-footer">
                                <span class="mode-card-badge">Multiplayer</span>
                                <span class="mode-card-players">2–4 Players</span>
                            </div>
                            <span class="mode-card-check">✓</span>
                        </button>
                        <button type="button" class="mode-card bots" data-mode="bots" role="tab" aria-selected="false">
                            <span class="mode-card-glow"></span>
                            <span class="mode-card-icon-wrap">
                                <span class="mode-card-icon">🤖</span>
                                <span class="mode-card-icon-pulse"></span>
                            </span>
                            <span class="mode-card-title">Play with Bots</span>
                            <span class="mode-card-desc">Practice against AI opponents. Choose difficulty and number of bots.</span>
                            <div class="mode-card-footer">
                                <span class="mode-card-badge">Single Player</span>
                                <span class="mode-card-players">1–3 Bots</span>
                            </div>
                            <span class="mode-card-check">✓</span>
                        </button>
                    </div>
                    <div class="mode-hint">
                        <span class="mode-hint-icon">💡</span>
                        <span class="mode-hint-text" id="modeHintText">Click a mode to select — <strong>Play with Friends</strong> lets you create or join multiplayer rooms</span>
                    </div>
                </div>
            </div>

            <!-- Friends Mode Options (default visible) -->
            <div class="mode-options" id="friendsOptions">
                <div class="create-room-row">
                    <button class="btn btn-primary" id="createRoomBtn">Create New Room</button>
                    <button class="btn btn-secondary" id="customSettingsBtn" type="button">⚙️ Custom Settings</button>
                </div>

                <div class="lobby-actions">
                    <input type="text" id="roomCodeInput" placeholder="Room code" maxlength="10" autocomplete="off">
                    <button class="btn btn-success" id="joinRoomBtn">Join</button>
                </div>
            </div>

            <!-- Bots Mode Options (hidden by default) -->
            <div class="mode-options bot-options-panel" id="botsOptions" style="display:none;">
                <h4>Bot Settings</h4>
                <div class="bot-options-grid">
                    <div class="bot-option-group">
                        <label for="botCount">Number of Bots</label>
                        <select id="botCount" class="bot-select">
                            <option value="1">1 Bot</option>
                            <option value="2" selected>2 Bots</option>
                            <option value="3">3 Bots</option>
                        </select>
                    </div>
                    <div class="bot-option-group">
                        <label for="botDifficulty">Difficulty</label>
                        <select id="botDifficulty" class="bot-select">
                            <option value="easy">😊 Easy</option>
                            <option value="normal" selected>😐 Normal</option>
                            <option value="hard">😤 Hard</option>
                            <option value="impossible">💀 Impossible</option>
                        </select>
                    </div>
                </div>
                <div class="difficulty-indicator" id="difficultyIndicator">
                    <span class="difficulty-indicator-icon" id="difficultyIcon">😐</span>
                    <div class="difficulty-indicator-info">
                        <div class="difficulty-indicator-title" id="difficultyTitle">Normal</div>
                        <div class="difficulty-indicator-desc" id="difficultyDesc">Normal bots play competitively but make occasional mistakes.</div>
                    </div>
                    <div class="difficulty-indicator-bar">
                        <div class="difficulty-indicator-fill normal" id="difficultyBar"></div>
                    </div>
                </div>
                <button class="btn btn-primary btn-create-bot" id="createBotRoomBtn">Create Bot Room</button>
            </div>

            <div class="room-list">
                <h3>Active Rooms</h3>
                <div id="roomListContainer">
                    <p class="empty-msg">No active rooms. Create one!</p>
                </div>
            </div>

            <div class="lobby-footer">
                <button class="btn btn-secondary" id="logoutBtn">Logout</button>
            </div>
        </div>

        <!-- Custom Room Settings Modal -->
        <div class="modal-overlay" id="customSettingsModal">
            <div class="modal-content custom-settings-modal">
                <div class="modal-header">
                    <h3>Custom Room Settings</h3>
                    <button class="modal-close" id="closeCustomSettingsModal" aria-label="Close">&times;</button>
                </div>
                <div class="modal-body">
                    <div class="setting-group">
                        <h4>Gameplay</h4>
                        <div class="setting-row">
                            <label for="customGridSize">Grid Size</label>
                            <input type="number" id="customGridSize" min="15" max="50" value="30" step="1">
                        </div>
                        <div class="setting-row">
                            <label for="customTickRate">Tick Rate (ms)</label>
                            <input type="number" id="customTickRate" min="50" max="500" value="150" step="10">
                        </div>
                        <div class="setting-row">
                            <label for="customMaxPlayers">Max Players</label>
                            <input type="number" id="customMaxPlayers" min="2" max="8" value="4" step="1">
                        </div>
                        <div class="setting-row">
                            <label for="customFoodDensity">Food Density</label>
                            <input type="number" id="customFoodDensity" min="0.5" max="3.0" value="1.0" step="0.1">
                        </div>
                    </div>
                    <div class="setting-group">
                        <h4>Features</h4>
                        <div class="setting-row toggle-row">
                            <label for="customEnableBoost">Enable Boost</label>
                            <label class="toggle-label">
                                <input type="checkbox" id="customEnableBoost" checked>
                                <span class="toggle-slider"></span>
                            </label>
                        </div>
                        <div class="setting-row toggle-row">
                            <label for="customEnableGoldenFood">Enable Golden Food</label>
                            <label class="toggle-label">
                                <input type="checkbox" id="customEnableGoldenFood" checked>
                                <span class="toggle-slider"></span>
                            </label>
                        </div>
                    </div>
                </div>
                <div class="modal-footer">
                    <button class="btn btn-secondary" id="resetCustomSettingsBtn" type="button">Reset to Defaults</button>
                    <button class="btn btn-primary" id="applyCustomSettingsBtn" type="button">Apply Settings</button>
                </div>
            </div>
        </div>

        <div id="errorMsg" class="error-msg" style="display:none;"></div>

        <footer class="page-footer">🐍 Multiplayer Snake — have fun!</footer>
    </div>

    <script src="js/ajax.js"></script>
    <script>
        // Handle mobile keyboard
        (function() {
            var focused = false;
            document.addEventListener('focusin', function() { focused = true; });
            document.addEventListener('focusout', function() { focused = false; });
            var lastHeight = window.innerHeight;
            setInterval(function() {
                var h = window.innerHeight;
                if (focused && h < lastHeight - 60) {
                    document.getElementById('app').style.paddingBottom = (lastHeight - h + 40) + 'px';
                } else if (!focused || h >= lastHeight - 60) {
                    document.getElementById('app').style.paddingBottom = '20px';
                }
                lastHeight = h;
            }, 300);
        })();

        let selectedColor = '#e94560';
        let currentUser = null;
        let isGuest = true;
        let currentGameMode = 'friends'; // 'friends' or 'bots'

        // Tab switching
        document.querySelectorAll('.auth-tab').forEach(function(tab) {
            tab.addEventListener('click', function() {
                document.querySelectorAll('.auth-tab').forEach(function(t) { t.classList.remove('active'); });
                document.querySelectorAll('.auth-panel').forEach(function(p) { p.classList.remove('active'); });
                this.classList.add('active');
                document.getElementById('panel' + this.dataset.tab.charAt(0).toUpperCase() + this.dataset.tab.slice(1)).classList.add('active');
                document.getElementById('loginError').style.display = 'none';
                document.getElementById('regError').style.display = 'none';
            });
        });

        // Game Mode switching (card-based)
        document.querySelectorAll('.mode-card').forEach(function(card) {
            card.addEventListener('click', function() {
                document.querySelectorAll('.mode-card').forEach(function(c) { 
                    c.classList.remove('active'); 
                    c.setAttribute('aria-selected', 'false');
                });
                document.querySelectorAll('.mode-options').forEach(function(o) { o.style.display = 'none'; });
                this.classList.add('active');
                this.setAttribute('aria-selected', 'true');
                var mode = this.dataset.mode;
                currentGameMode = mode;
                document.getElementById(mode + 'Options').style.display = 'block';
                
                // Update hint text
                var hintText = document.getElementById('modeHintText');
                if (mode === 'friends') {
                    hintText.innerHTML = 'Click a mode to select — <strong>Play with Friends</strong> lets you create or join multiplayer rooms';
                } else {
                    hintText.innerHTML = 'Click a mode to select — <strong>Play with Bots</strong> lets you practice against AI with adjustable difficulty';
                }
            });
        });

        // Bot difficulty description + dynamic indicator
        var difficultyData = {
            'easy': { 
                desc: 'Easy bots move randomly and make frequent mistakes. Good for practice.',
                icon: '😊',
                title: 'Easy',
                barClass: 'easy'
            },
            'normal': { 
                desc: 'Normal bots play competitively but make occasional mistakes.',
                icon: '😐',
                title: 'Normal',
                barClass: 'normal'
            },
            'hard': { 
                desc: 'Hard bots are aggressive, predict your moves, and rarely make errors.',
                icon: '😤',
                title: 'Hard',
                barClass: 'hard'
            },
            'impossible': { 
                desc: 'Impossible bots have perfect reflexes, predict all outcomes, and never make mistakes. You cannot win.',
                icon: '💀',
                title: 'Impossible',
                barClass: 'impossible'
            }
        };
        document.getElementById('botDifficulty').addEventListener('change', function() {
            var data = difficultyData[this.value] || difficultyData.normal;
            document.getElementById('difficultyDesc').textContent = data.desc;
            document.getElementById('difficultyIcon').textContent = data.icon;
            document.getElementById('difficultyTitle').textContent = data.title;
            var bar = document.getElementById('difficultyBar');
            bar.className = 'difficulty-indicator-fill ' + data.barClass;
        });

        // Switch between login/register via links
        document.querySelectorAll('[data-switch]').forEach(function(link) {
            link.addEventListener('click', function(e) {
                e.preventDefault();
                var tab = this.dataset.switch;
                document.querySelectorAll('.auth-tab').forEach(function(t) { t.classList.remove('active'); });
                document.querySelectorAll('.auth-panel').forEach(function(p) { p.classList.remove('active'); });
                document.querySelector('.auth-tab[data-tab="' + tab + '"]').classList.add('active');
                document.getElementById('panel' + tab.charAt(0).toUpperCase() + tab.slice(1)).classList.add('active');
                document.getElementById('loginError').style.display = 'none';
                document.getElementById('regError').style.display = 'none';
            });
        });

        function setLoading(btn, loading) {
            if (loading) {
                btn.disabled = true;
                btn._origText = btn.textContent;
                btn.textContent = 'Please wait...';
            } else {
                btn.disabled = false;
                if (btn._origText) btn.textContent = btn._origText;
            }
        }

        // Guest play
        document.getElementById('guestBtn').addEventListener('click', function() {
            isGuest = true;
            var ts = Date.now().toString(36).toUpperCase();
            currentUser = 'Guest_' + Math.random().toString(36).substring(2, 6).toUpperCase() + ts.slice(-2);
            showLobby();
        });

        // Login with Remember Me
        document.getElementById('loginBtn').addEventListener('click', function() {
            var btn = this;
            var username = document.getElementById('loginUsername').value.trim();
            var password = document.getElementById('loginPassword').value;
            var remember = document.getElementById('rememberMe').checked;
            if (!username || !password) {
                showAuthError('loginError', 'Fill in all fields');
                return;
            }
            setLoading(btn, true);
            Ajax.post('api/auth', { action: 'login', username: username, password: password, remember: remember }, function(data) {
                setLoading(btn, false);
                if (data.success) {
                    isGuest = false;
                    currentUser = data.username;
                    if (remember && data.token) {
                        try { localStorage.setItem('snake_token', data.token); } catch(e) {}
                    }
                    showLobby();
                } else {
                    showAuthError('loginError', data.error || 'Login failed');
                }
            });
        });

        // Auto-login with saved token
        (function() {
            var token;
            try { token = localStorage.getItem('snake_token'); } catch(e) {}
            if (token) {
                Ajax.post('api/auth', { action: 'remember', token: token }, function(data) {
                    if (data.success) {
                        isGuest = false;
                        currentUser = data.username;
                        showLobby();
                    } else {
                        try { localStorage.removeItem('snake_token'); } catch(e) {}
                    }
                });
            }
        })();

        // Register
        document.getElementById('regBtn').addEventListener('click', function() {
            var btn = this;
            var username = document.getElementById('regUsername').value.trim();
            var password = document.getElementById('regPassword').value;
            var confirm = document.getElementById('regConfirm').value;
            if (!username || !password || !confirm) {
                showAuthError('regError', 'Fill in all fields');
                return;
            }
            if (password !== confirm) {
                showAuthError('regError', 'Passwords do not match');
                return;
            }
            if (password.length < 4) {
                showAuthError('regError', 'Password must be at least 4 characters');
                return;
            }
            setLoading(btn, true);
            Ajax.post('api/auth', { action: 'register', username: username, password: password }, function(data) {
                setLoading(btn, false);
                if (data.success) {
                    isGuest = false;
                    currentUser = data.username;
                    showLobby();
                } else {
                    showAuthError('regError', data.error || 'Registration failed');
                }
            });
        });

        function showAuthError(id, msg) {
            var el = document.getElementById(id);
            el.textContent = msg;
            el.style.display = 'block';
        }

        function showLobby() {
            document.getElementById('authScreen').style.display = 'none';
            document.getElementById('lobby').style.display = 'block';
            var logoutBtn = document.getElementById('logoutBtn');
            if (isGuest) {
                document.getElementById('lobbyUser').innerHTML = '<span class="guest-badge">👤 Guest</span>';
                document.getElementById('playerName').value = currentUser;
                if (logoutBtn) logoutBtn.textContent = 'Back';
            } else {
                document.getElementById('lobbyUser').innerHTML = '<span class="user-badge">✅ ' + currentUser + '</span>' +
                    '<a href="profile.jsp" class="icon-btn profile-btn" title="View Profile">👤</a>';
                document.getElementById('playerName').value = currentUser;
                if (logoutBtn) logoutBtn.textContent = 'Logout';
            }
        }

        // Logout/Back: clear remember-me token and return to auth screen
        document.getElementById('logoutBtn').addEventListener('click', function() {
            if (!isGuest) {
                try { localStorage.removeItem('snake_token'); } catch(e) {}
            }
            document.getElementById('authScreen').style.display = 'block';
            document.getElementById('lobby').style.display = 'none';
            currentUser = null;
            isGuest = true;
        });

        // Color picker
        document.querySelectorAll('.color-option').forEach(function(el) {
            el.addEventListener('click', function() {
                document.querySelectorAll('.color-option').forEach(function(o) { o.classList.remove('selected'); });
                this.classList.add('selected');
                selectedColor = this.dataset.color;
            });
        });

        // Create room (Friends mode)
        // Custom Room Settings Modal
        var customSettings = {
            gridSize: 30,
            tickRateMs: 150,
            maxPlayers: 4,
            foodDensity: 1.0,
            enableBoost: true,
            enableGoldenFood: true,
            hasCustomSettings: false
        };

        function openCustomSettingsModal() {
            document.getElementById('customSettingsModal').classList.add('open');
            document.getElementById('customGridSize').value = customSettings.gridSize;
            document.getElementById('customTickRate').value = customSettings.tickRateMs;
            document.getElementById('customMaxPlayers').value = customSettings.maxPlayers;
            document.getElementById('customFoodDensity').value = customSettings.foodDensity;
            document.getElementById('customEnableBoost').checked = customSettings.enableBoost;
            document.getElementById('customEnableGoldenFood').checked = customSettings.enableGoldenFood;
        }

        function closeCustomSettingsModal() {
            document.getElementById('customSettingsModal').classList.remove('open');
        }

        function applyCustomSettings() {
            customSettings.gridSize = parseInt(document.getElementById('customGridSize').value, 10);
            customSettings.tickRateMs = parseInt(document.getElementById('customTickRate').value, 10);
            customSettings.maxPlayers = parseInt(document.getElementById('customMaxPlayers').value, 10);
            customSettings.foodDensity = parseFloat(document.getElementById('customFoodDensity').value);
            customSettings.enableBoost = document.getElementById('customEnableBoost').checked;
            customSettings.enableGoldenFood = document.getElementById('customEnableGoldenFood').checked;
            customSettings.hasCustomSettings = true;
            
            if (customSettings.gridSize < 15 || customSettings.gridSize > 50) customSettings.gridSize = 30;
            if (customSettings.tickRateMs < 50 || customSettings.tickRateMs > 500) customSettings.tickRateMs = 150;
            if (customSettings.maxPlayers < 2 || customSettings.maxPlayers > 8) customSettings.maxPlayers = 4;
            if (customSettings.foodDensity < 0.5 || customSettings.foodDensity > 3.0) customSettings.foodDensity = 1.0;
            
            closeCustomSettingsModal();
            showError('Custom settings applied! Click "Create New Room" to create a room with these settings.', 5000);
        }

        function resetCustomSettings() {
            customSettings.gridSize = 30;
            customSettings.tickRateMs = 150;
            customSettings.maxPlayers = 4;
            customSettings.foodDensity = 1.0;
            customSettings.enableBoost = true;
            customSettings.enableGoldenFood = true;
            customSettings.hasCustomSettings = false;
            
            document.getElementById('customGridSize').value = customSettings.gridSize;
            document.getElementById('customTickRate').value = customSettings.tickRateMs;
            document.getElementById('customMaxPlayers').value = customSettings.maxPlayers;
            document.getElementById('customFoodDensity').value = customSettings.foodDensity;
            document.getElementById('customEnableBoost').checked = customSettings.enableBoost;
            document.getElementById('customEnableGoldenFood').checked = customSettings.enableGoldenFood;
            
            showError('Settings reset to defaults.', 3000);
        }

        document.getElementById('customSettingsBtn').addEventListener('click', openCustomSettingsModal);
        document.getElementById('closeCustomSettingsModal').addEventListener('click', closeCustomSettingsModal);
        document.getElementById('applyCustomSettingsBtn').addEventListener('click', applyCustomSettings);
        document.getElementById('resetCustomSettingsBtn').addEventListener('click', resetCustomSettings);

        document.getElementById('customSettingsModal').addEventListener('click', function(e) {
            if (e.target === this) closeCustomSettingsModal();
        });

        document.addEventListener('keydown', function(e) {
            if (e.key === 'Escape') closeCustomSettingsModal();
        });

        // Create room (Friends mode)
        document.getElementById('createRoomBtn').addEventListener('click', function() {
            var btn = this;
            var name = document.getElementById('playerName').value.trim();
            if (!name) { showError('Enter your name first'); return; }
            setLoading(btn, true);
            
            var createData = { action: 'create', playerName: name, color: selectedColor, gameMode: 'friends' };
            if (customSettings.hasCustomSettings) {
                createData.gridSize = customSettings.gridSize;
                createData.tickRateMs = customSettings.tickRateMs;
                createData.maxPlayers = customSettings.maxPlayers;
                createData.foodDensity = customSettings.foodDensity;
                createData.enableBoost = customSettings.enableBoost;
                createData.enableGoldenFood = customSettings.enableGoldenFood;
            }
            
            Ajax.post('api/room', createData, function(data) {
                setLoading(btn, false);
                if (data.success) {
                    window.location.href = 'game.jsp?room=' + data.roomCode + '&player=' + encodeURIComponent(name) + '&color=' + encodeURIComponent(selectedColor) + '&guest=' + isGuest;
                } else {
                    showError(data.error || 'Failed to create room');
                }
            });
        });

        // Create bot room
        document.getElementById('createBotRoomBtn').addEventListener('click', function() {
            var btn = this;
            var name = document.getElementById('playerName').value.trim();
            if (!name) { showError('Enter your name first'); return; }
            var botCount = parseInt(document.getElementById('botCount').value, 10);
            var botDifficulty = document.getElementById('botDifficulty').value;
            setLoading(btn, true);
            Ajax.post('api/room', { 
                action: 'create', 
                playerName: name, 
                color: selectedColor, 
                gameMode: 'bots',
                botCount: botCount,
                botDifficulty: botDifficulty
            }, function(data) {
                setLoading(btn, false);
                if (data.success) {
                    window.location.href = 'game.jsp?room=' + data.roomCode + '&player=' + encodeURIComponent(name) + '&color=' + encodeURIComponent(selectedColor) + '&guest=' + isGuest;
                } else {
                    showError(data.error || 'Failed to create bot room');
                }
            });
        });

        // Join room
        document.getElementById('joinRoomBtn').addEventListener('click', function() {
            var btn = this;
            var name = document.getElementById('playerName').value.trim();
            var code = document.getElementById('roomCodeInput').value.trim().toUpperCase();
            if (!name) { showError('Enter your name first'); return; }
            if (!code) { showError('Enter a room code'); return; }
            setLoading(btn, true);
            Ajax.post('api/room', { action: 'join', roomCode: code, playerName: name, color: selectedColor }, function(data) {
                setLoading(btn, false);
                if (data.success) {
                    window.location.href = 'game.jsp?room=' + data.roomCode + '&player=' + encodeURIComponent(name) + '&color=' + encodeURIComponent(selectedColor) + '&guest=' + isGuest;
                } else {
                    showError(data.error || 'Failed to join room');
                }
            });
        });

        // Refresh room list
        function refreshRooms() {
            Ajax.get('api/room?action=list', function(data) {
                var container = document.getElementById('roomListContainer');
                if (!data.rooms || data.rooms.length === 0) {
                    container.innerHTML = '<p class="empty-msg">No active rooms. Create one!</p>';
                    return;
                }
                var html = '';
                data.rooms.forEach(function(room) {
                    html += '<div class="room-item">' +
                        '<div class="room-info">' +
                        '<span class="room-name">Room ' + room.code + '</span>' +
                        '<span class="room-players">' + room.playerCount + '/4 players</span>' +
                        '</div>' +
                        '<button class="btn btn-success" onclick="quickJoin(\'' + room.code + '\')">Join</button>' +
                        '</div>';
                });
                container.innerHTML = html;
            });
        }

        function quickJoin(code) {
            var name = document.getElementById('playerName').value.trim();
            if (!name) { showError('Enter your name first'); return; }
            Ajax.post('api/room', { action: 'join', roomCode: code, playerName: name, color: selectedColor }, function(data) {
                if (data.success) {
                    window.location.href = 'game.jsp?room=' + data.roomCode + '&player=' + encodeURIComponent(name) + '&color=' + encodeURIComponent(selectedColor) + '&guest=' + isGuest;
                } else {
                    showError(data.error || 'Failed to join room');
                }
            });
        }

        function showError(msg, duration) {
            var el = document.getElementById('errorMsg');
            el.textContent = msg;
            el.style.display = 'block';
            setTimeout(function() { el.style.display = 'none'; }, duration || 3000);
        }

        // Enter key support for login/register
        document.getElementById('loginPassword').addEventListener('keydown', function(e) {
            if (e.key === 'Enter') document.getElementById('loginBtn').click();
        });
        document.getElementById('regConfirm').addEventListener('keydown', function(e) {
            if (e.key === 'Enter') document.getElementById('regBtn').click();
        });
        document.getElementById('roomCodeInput').addEventListener('keydown', function(e) {
            if (e.key === 'Enter') document.getElementById('joinRoomBtn').click();
        });
        document.getElementById('playerName').addEventListener('keydown', function(e) {
            if (e.key === 'Enter') document.getElementById('createRoomBtn').click();
        });

        // Auto-refresh room list every 3 seconds
        refreshRooms();
        setInterval(refreshRooms, 3000);
    </script>
</body>
</html>
