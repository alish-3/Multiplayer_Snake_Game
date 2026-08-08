<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0, user-scalable=no">
    <meta name="theme-color" content="#0d1021">
    <link rel="manifest" href="manifest.webmanifest">
    <link rel="apple-touch-icon" href="icons/apple-touch-icon.png">
    <meta name="mobile-web-app-capable" content="yes">
    <meta name="apple-mobile-web-app-capable" content="yes">
    <meta name="apple-mobile-web-app-status-bar-style" content="black-translucent">
    <title>Snake Game - Lobby</title>
    <link rel="icon" href="data:image/svg+xml,<svg xmlns=%22http://www.w3.org/2000/svg%22 viewBox=%220 0 100 100%22><text y=%22.9em%22 font-size=%2290%22>🐍</text></svg>">
    <link rel="stylesheet" href="css/style.css?v=4">
    <script src="js/i18n.js"></script>
</head>
<body class="lobby-page">
    <div class="lobby-container" id="app">
        <button id="langToggle" class="lang-toggle" aria-label="Switch language" title="Switch language">🌐 <span id="langToggleLabel">EN</span></button>
        <h1 class="game-title" data-i18n="appName">🐍 Multiplayer Snake</h1>

        <!-- AUTH SCREEN -->
        <div class="auth-card" id="authScreen">
            <div class="auth-tabs" role="tablist">
                <button class="auth-tab active" data-tab="guest" role="tab" data-i18n="guest">Guest</button>
                <button class="auth-tab" data-tab="login" role="tab" data-i18n="login">Login</button>
                <button class="auth-tab" data-tab="register" role="tab" data-i18n="register">Register</button>
            </div>

            <!-- GUEST -->
            <div class="auth-panel active" id="panelGuest">
                <p class="auth-desc" data-i18n="guestDesc">Play without an account. Your scores and progress will not be saved.</p>
                <div class="auth-notice" data-i18n="guestNotice">⚠️ Your data will not be saved as a guest!</div>
                <button class="btn btn-primary" id="guestBtn" data-i18n="playAsGuest" data-i18n-aria="playAsGuest">Play as Guest</button>
            </div>

            <!-- LOGIN -->
            <div class="auth-panel" id="panelLogin">
                <div class="form-group">
                    <label for="loginUsername" data-i18n="username">Username</label>
                    <input type="text" id="loginUsername" placeholder="Enter username" maxlength="50" autocomplete="username" data-i18n-placeholder="enterUsername">
                </div>
                <div class="form-group">
                    <label for="loginPassword" data-i18n="password">Password</label>
                    <input type="password" id="loginPassword" placeholder="Enter password" maxlength="100" autocomplete="current-password" data-i18n-placeholder="enterPassword">
                </div>
                <div class="auth-error" id="loginError" role="alert" aria-live="polite"></div>
                <label class="remember-label">
                    <input type="checkbox" id="rememberMe"> <span data-i18n="rememberMe">Remember me (30 days)</span>
                </label>
                <button class="btn btn-primary" id="loginBtn" data-i18n="login" data-i18n-aria="login">Login</button>
                <p class="auth-switch"><span data-i18n="noAccount">Don't have an account?</span> <a href="#" data-switch="register" data-i18n="registerHere">Register here</a></p>
            </div>

            <!-- REGISTER -->
            <div class="auth-panel" id="panelRegister">
                <div class="form-group">
                    <label for="regUsername" data-i18n="username">Username</label>
                    <input type="text" id="regUsername" placeholder="Choose a username" maxlength="50" autocomplete="username" data-i18n-placeholder="chooseUsername">
                </div>
                <div class="form-group">
                    <label for="regPassword" data-i18n="password">Password</label>
                    <input type="password" id="regPassword" placeholder="Choose a password" maxlength="100" autocomplete="new-password" data-i18n-placeholder="choosePassword">
                </div>
                <div class="form-group">
                    <label for="regConfirm" data-i18n="confirmPassword">Confirm Password</label>
                    <input type="password" id="regConfirm" placeholder="Confirm password" maxlength="100" autocomplete="new-password" data-i18n-placeholder="confirmPasswordPh">
                </div>
                <div class="auth-error" id="regError" role="alert" aria-live="polite"></div>
                <button class="btn btn-primary" id="regBtn" data-i18n="createAccount" data-i18n-aria="createAccount">Create Account</button>
                <p class="auth-switch"><span data-i18n="hasAccount">Already have an account?</span> <a href="#" data-switch="login" data-i18n="loginHere">Login here</a></p>
            </div>
        </div>

        <!-- LOBBY SCREEN -->
        <div class="lobby-card" id="lobby" style="display:none;">
            <div class="lobby-user" id="lobbyUser"></div>
            <div class="form-group">
                <label for="playerName" data-i18n="yourName">Your Name</label>
                <input type="text" id="playerName" placeholder="Enter your name..." maxlength="20" autocomplete="off" data-i18n-placeholder="yourNamePh">
            </div>

            <div class="form-group">
                <label data-i18n="yourSnakeColor">Your Snake Color</label>
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
                    <h4 data-i18n="chooseHowToPlay">Choose How to Play</h4>
                    <div class="mode-cards">
                        <button type="button" class="mode-card friends active" data-mode="friends" role="tab" aria-selected="true">
                            <span class="mode-card-glow"></span>
                            <span class="mode-card-icon-wrap">
                                <span class="mode-card-icon">👥</span>
                                <span class="mode-card-icon-pulse"></span>
                            </span>
                            <span class="mode-card-title" data-i18n="playWithFriends">Play with Friends</span>
                            <span class="mode-card-desc" data-i18n="friendsDesc">Create or join rooms with other players. Share room codes and play together!</span>
                            <div class="mode-card-footer">
                                <span class="mode-card-badge" data-i18n="multiplayer">Multiplayer</span>
                                <span class="mode-card-players" data-i18n="players2to4">2–4 Players</span>
                            </div>
                            <span class="mode-card-check">✓</span>
                        </button>
                        <button type="button" class="mode-card bots" data-mode="bots" role="tab" aria-selected="false">
                            <span class="mode-card-glow"></span>
                            <span class="mode-card-icon-wrap">
                                <span class="mode-card-icon">🤖</span>
                                <span class="mode-card-icon-pulse"></span>
                            </span>
                            <span class="mode-card-title" data-i18n="playWithBots">Play with Bots</span>
                            <span class="mode-card-desc" data-i18n="botsDesc">Practice against AI opponents. Choose difficulty and number of bots.</span>
                            <div class="mode-card-footer">
                                <span class="mode-card-badge" data-i18n="singlePlayer">Single Player</span>
                                <span class="mode-card-players" data-i18n="bots1to3">1–3 Bots</span>
                            </div>
                            <span class="mode-card-check">✓</span>
                        </button>
                    </div>
                    <div class="mode-hint">
                        <span class="mode-hint-icon">💡</span>
                        <span class="mode-hint-text" id="modeHintText" data-i18n="modeHintFriends" data-i18n-html>Click a mode to select — <strong>Play with Friends</strong> lets you create or join multiplayer rooms</span>
                    </div>
                </div>
            </div>

            <!-- Friends Mode Options (default visible) -->
            <div class="mode-options" id="friendsOptions">
                <div class="create-room-row">
                    <button class="btn btn-primary" id="createRoomBtn" data-i18n="createNewRoom" data-i18n-aria="createNewRoom">Create New Room</button>
                    <button class="btn btn-secondary" id="customSettingsBtn" type="button" data-i18n="customSettings" data-i18n-aria="customSettings">⚙️ Custom Settings</button>
                </div>

                <div class="lobby-actions">
                    <input type="text" id="roomCodeInput" placeholder="Room code" maxlength="10" autocomplete="off" data-i18n-placeholder="roomCode">
                    <button class="btn btn-success" id="joinRoomBtn" data-i18n="join" data-i18n-aria="join">Join</button>
                </div>
            </div>

            <!-- Bots Mode Options (hidden by default) -->
            <div class="mode-options bot-options-panel" id="botsOptions" style="display:none;">
                <h4 data-i18n="botSettings">Bot Settings</h4>
                <div class="bot-options-grid">
                    <div class="bot-option-group">
                        <label for="botCount" data-i18n="numberOfBots">Number of Bots</label>
                        <select id="botCount" class="bot-select">
                            <option value="1" data-i18n="bot1">1 Bot</option>
                            <option value="2" selected data-i18n="bot2">2 Bots</option>
                            <option value="3" data-i18n="bot3">3 Bots</option>
                        </select>
                    </div>
                    <div class="bot-option-group">
                        <label for="botDifficulty" data-i18n="difficulty">Difficulty</label>
                        <select id="botDifficulty" class="bot-select">
                            <option value="easy" data-i18n="easy">😊 Easy</option>
                            <option value="normal" selected data-i18n="normal">😐 Normal</option>
                            <option value="hard" data-i18n="hard">😤 Hard</option>
                            <option value="impossible" data-i18n="impossible">💀 Impossible</option>
                        </select>
                    </div>
                </div>
                <div class="difficulty-indicator" id="difficultyIndicator">
                    <span class="difficulty-indicator-icon" id="difficultyIcon">😐</span>
                    <div class="difficulty-indicator-info">
                        <div class="difficulty-indicator-title" id="difficultyTitle" data-i18n="normal">Normal</div>
                        <div class="difficulty-indicator-desc" id="difficultyDesc" data-i18n="normalDesc">Normal bots play competitively but make occasional mistakes.</div>
                    </div>
                    <div class="difficulty-indicator-bar">
                        <div class="difficulty-indicator-fill normal" id="difficultyBar"></div>
                    </div>
                </div>
                <button class="btn btn-primary btn-create-bot" id="createBotRoomBtn" data-i18n="createBotRoom" data-i18n-aria="createBotRoom">Create Bot Room</button>
            </div>

            <div class="room-list">
                <h3 data-i18n="activeRooms">Active Rooms</h3>
                <div id="roomListContainer">
                    <p class="empty-msg" data-i18n="noActiveRooms">No active rooms. Create one!</p>
                </div>
            </div>

            <div class="lobby-footer">
                <button class="btn btn-secondary" id="logoutBtn" data-i18n="logout" data-i18n-aria="logout">Logout</button>
            </div>
        </div>

        <!-- Custom Room Settings Modal -->
        <div class="modal-overlay" id="customSettingsModal" role="dialog" aria-modal="true" aria-labelledby="customSettingsTitle">
            <div class="modal-content custom-settings-modal">
                <div class="modal-header">
                    <h3 id="customSettingsTitle" data-i18n="customRoomSettings">Custom Room Settings</h3>
                    <button class="modal-close" id="closeCustomSettingsModal" aria-label="Close" data-i18n-aria="close">&times;</button>
                </div>
                <div class="modal-body">
                    <div class="setting-group">
                        <h4 data-i18n="gameplay">Gameplay</h4>
                        <div class="setting-row">
                            <label for="customGridSize" data-i18n="gridSize">Grid Size</label>
                            <input type="number" id="customGridSize" min="15" max="50" value="30" step="1">
                        </div>
                        <div class="setting-row">
                            <label for="customTickRate" data-i18n="tickRateMs">Tick Rate (ms)</label>
                            <input type="number" id="customTickRate" min="50" max="500" value="150" step="10">
                        </div>
                        <div class="setting-row">
                            <label for="customMaxPlayers" data-i18n="maxPlayers">Max Players</label>
                            <input type="number" id="customMaxPlayers" min="2" max="8" value="4" step="1">
                        </div>
                        <div class="setting-row">
                            <label for="customFoodDensity" data-i18n="foodDensity">Food Density</label>
                            <input type="number" id="customFoodDensity" min="0.5" max="3.0" value="1.0" step="0.1">
                        </div>
                    </div>
                    <div class="setting-group">
                        <h4 data-i18n="features">Features</h4>
                        <div class="setting-row toggle-row">
                            <label for="customEnableBoost" data-i18n="enableBoost">Enable Boost</label>
                            <label class="toggle-label">
                                <input type="checkbox" id="customEnableBoost" checked>
                                <span class="toggle-slider"></span>
                            </label>
                        </div>
                        <div class="setting-row toggle-row">
                            <label for="customEnableGoldenFood" data-i18n="enableGoldenFood">Enable Golden Food</label>
                            <label class="toggle-label">
                                <input type="checkbox" id="customEnableGoldenFood" checked>
                                <span class="toggle-slider"></span>
                            </label>
                        </div>
                    </div>
                </div>
                <div class="modal-footer">
                    <button class="btn btn-secondary" id="resetCustomSettingsBtn" type="button" data-i18n="resetToDefaults">Reset to Defaults</button>
                    <button class="btn btn-primary" id="applyCustomSettingsBtn" type="button" data-i18n="applySettings">Apply Settings</button>
                </div>
            </div>
        </div>

        <div id="errorMsg" class="error-msg" style="display:none;" role="alert" aria-live="polite"></div>

        <footer class="page-footer" data-i18n="footer">🐍 Multiplayer Snake — have fun!</footer>
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
                
                // Update hint text (i18n)
                var hintText = document.getElementById('modeHintText');
                if (hintText) {
                    hintText.innerHTML = mode === 'friends' ? I18n.t('modeHintFriends') : I18n.t('modeHintBots');
                }
                refreshDynamicI18n();
            });
        });

        // Bot difficulty description + dynamic indicator
        var difficultyData = {
            'easy': {
                descKey: 'easyDesc',
                icon: '😊',
                titleKey: 'easy',
                barClass: 'easy'
            },
            'normal': {
                descKey: 'normalDesc',
                icon: '😐',
                titleKey: 'normal',
                barClass: 'normal'
            },
            'hard': {
                descKey: 'hardDesc',
                icon: '😤',
                titleKey: 'hard',
                barClass: 'hard'
            },
            'impossible': {
                descKey: 'impossibleDesc',
                icon: '💀',
                titleKey: 'impossible',
                barClass: 'impossible'
            }
        };
        document.getElementById('botDifficulty').addEventListener('change', function() {
            var data = difficultyData[this.value] || difficultyData.normal;
            document.getElementById('difficultyDesc').textContent = I18n.t(data.descKey);
            document.getElementById('difficultyIcon').textContent = data.icon;
            document.getElementById('difficultyTitle').textContent = I18n.t(data.titleKey);
            var bar = document.getElementById('difficultyBar');
            bar.className = 'difficulty-indicator-fill ' + data.barClass;
            refreshDynamicI18n();
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
                btn.textContent = I18n.t('pleaseWait');
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
                showAuthError('loginError', I18n.t('fillAllFields'));
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
                    showAuthError('loginError', data.error || I18n.t('loginFailed'));
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
                showAuthError('regError', I18n.t('fillAllFields'));
                return;
            }
            if (password !== confirm) {
                showAuthError('regError', I18n.t('passwordsDontMatch'));
                return;
            }
            if (password.length < 4) {
                showAuthError('regError', I18n.t('passwordMin4'));
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
                    showAuthError('regError', data.error || I18n.t('registrationFailed'));
                }
            });
        });

        function showAuthError(id, msg) {
            var el = document.getElementById(id);
            el.textContent = msg;
            el.style.display = 'block';
        }

        function renderLobbyUser() {
            var lobbyUser = document.getElementById('lobbyUser');
            var logoutBtn = document.getElementById('logoutBtn');
            if (isGuest) {
                lobbyUser.innerHTML = '<span class="guest-badge">👤 ' + I18n.t('guest') + '</span>';
                if (logoutBtn) logoutBtn.textContent = I18n.t('back');
            } else {
                lobbyUser.innerHTML = '<span class="user-badge">✅ ' + currentUser + '</span>' +
                    '<a href="profile.jsp" class="icon-btn profile-btn" title="' + I18n.t('viewProfile') + '">👤</a>';
                if (logoutBtn) logoutBtn.textContent = I18n.t('logout');
            }
        }

        function showLobby() {
            document.getElementById('authScreen').style.display = 'none';
            document.getElementById('lobby').style.display = 'block';
            document.getElementById('playerName').value = currentUser;
            renderLobbyUser();
            refreshDynamicI18n();
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
            showError(I18n.t('settingsApplied'), 5000);
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
            
            showError(I18n.t('settingsReset'), 3000);
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
            if (!name) { showError(I18n.t('enterNameFirst')); return; }
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
                    showError(data.error || I18n.t('failedCreateRoom'));
                }
            });
        });

        // Create bot room
        document.getElementById('createBotRoomBtn').addEventListener('click', function() {
            var btn = this;
            var name = document.getElementById('playerName').value.trim();
            if (!name) { showError(I18n.t('enterNameFirst')); return; }
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
                    showError(data.error || I18n.t('failedCreateBotRoom'));
                }
            });
        });

        // Join room
        document.getElementById('joinRoomBtn').addEventListener('click', function() {
            var btn = this;
            var name = document.getElementById('playerName').value.trim();
            var code = document.getElementById('roomCodeInput').value.trim().toUpperCase();
            if (!name) { showError(I18n.t('enterNameFirst')); return; }
            if (!code) { showError(I18n.t('enterRoomCode')); return; }
            setLoading(btn, true);
            Ajax.post('api/room', { action: 'join', roomCode: code, playerName: name, color: selectedColor }, function(data) {
                setLoading(btn, false);
                if (data.success) {
                    window.location.href = 'game.jsp?room=' + data.roomCode + '&player=' + encodeURIComponent(name) + '&color=' + encodeURIComponent(selectedColor) + '&guest=' + isGuest;
                } else {
                    showError(data.error || I18n.t('failedJoinRoom'));
                }
            });
        });

        // Refresh room list
        var lastRooms = null;

        function renderRooms(rooms) {
            var container = document.getElementById('roomListContainer');
            if (!rooms || rooms.length === 0) {
                container.innerHTML = '<p class="empty-msg">' + I18n.t('noActiveRooms') + '</p>';
                return;
            }
            var html = '';
            rooms.forEach(function(room) {
                html += '<div class="room-item">' +
                    '<div class="room-info">' +
                    '<span class="room-name">' + I18n.t('room') + ' ' + room.code + '</span>' +
                    '<span class="room-players">' + room.playerCount + '/4 ' + I18n.t('players') + '</span>' +
                    '</div>' +
                    '<button class="btn btn-success" onclick="quickJoin(\'' + room.code + '\')">' + I18n.t('join') + '</button>' +
                    '</div>';
            });
            container.innerHTML = html;
        }

        function refreshRooms() {
            Ajax.get('api/room?action=list', function(data) {
                lastRooms = data.rooms || null;
                renderRooms(lastRooms);
            });
        }

        function quickJoin(code) {
            var name = document.getElementById('playerName').value.trim();
            if (!name) { showError(I18n.t('enterNameFirst')); return; }
            Ajax.post('api/room', { action: 'join', roomCode: code, playerName: name, color: selectedColor }, function(data) {
                if (data.success) {
                    window.location.href = 'game.jsp?room=' + data.roomCode + '&player=' + encodeURIComponent(name) + '&color=' + encodeURIComponent(selectedColor) + '&guest=' + isGuest;
                } else {
                    showError(data.error || I18n.t('failedJoinRoom'));
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

        // --- i18n: dynamic content refresh ---
        function refreshDynamicI18n() {
            if (!window.I18n) return;

            // Mode hint (depends on currently selected mode)
            var hintText = document.getElementById('modeHintText');
            if (hintText) {
                hintText.innerHTML = currentGameMode === 'friends' ? I18n.t('modeHintFriends') : I18n.t('modeHintBots');
            }

            // Difficulty indicator (depends on currently selected difficulty)
            var diffSelect = document.getElementById('botDifficulty');
            var diffData = difficultyData[diffSelect ? diffSelect.value : 'normal'] || difficultyData.normal;
            var diffTitle = document.getElementById('difficultyTitle');
            var diffDesc = document.getElementById('difficultyDesc');
            if (diffTitle) diffTitle.textContent = I18n.t(diffData.titleKey);
            if (diffDesc) diffDesc.textContent = I18n.t(diffData.descKey);

            // Room list (re-render from last fetched data)
            renderRooms(lastRooms);

            // Lobby user badge + logout/back label
            renderLobbyUser();
        }

        function updateLangLabel() {
            var el = document.getElementById('langToggleLabel');
            if (el) el.textContent = (window.I18n && I18n.getLang() === 'ne') ? 'नेपाली' : 'EN';
        }

        var langBtn = document.getElementById('langToggle');
        if (langBtn) {
            langBtn.addEventListener('click', function() {
                if (window.I18n) I18n.toggleLang();
                updateLangLabel();
                refreshDynamicI18n();
            });
        }
        document.addEventListener('i18nchanged', function() {
            updateLangLabel();
            refreshDynamicI18n();
        });

        // Initial dynamic state should already be translated (i18n.js auto-applies static DOM)
        updateLangLabel();
        refreshDynamicI18n();
    </script>
</body>
</html>
