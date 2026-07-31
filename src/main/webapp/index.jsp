<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0, user-scalable=no">
    <meta name="theme-color" content="#0d1021">
    <title>Snake Game - Lobby</title>
    <link rel="icon" href="data:image/svg+xml,<svg xmlns=%22http://www.w3.org/2000/svg%22 viewBox=%220 0 100 100%22><text y=%22.9em%22 font-size=%2290%22>🐍</text></svg>">
    <link rel="stylesheet" href="css/style.css">
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

            <button class="btn btn-primary" id="createRoomBtn">Create New Room</button>

            <div class="lobby-actions">
                <input type="text" id="roomCodeInput" placeholder="Room code" maxlength="10" autocomplete="off">
                <button class="btn btn-success" id="joinRoomBtn">Join</button>
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
            if (isGuest) {
                document.getElementById('lobbyUser').innerHTML = '<span class="guest-badge">👤 Guest</span>';
                document.getElementById('playerName').value = currentUser;
            } else {
                document.getElementById('lobbyUser').innerHTML = '<span class="user-badge">✅ ' + currentUser + '</span>';
                document.getElementById('playerName').value = currentUser;
            }
        }

        // Logout: clear remember-me token and return to auth screen
        document.getElementById('logoutBtn').addEventListener('click', function() {
            try { localStorage.removeItem('snake_token'); } catch(e) {}
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

        // Create room
        document.getElementById('createRoomBtn').addEventListener('click', function() {
            var btn = this;
            var name = document.getElementById('playerName').value.trim();
            if (!name) { showError('Enter your name first'); return; }
            setLoading(btn, true);
            Ajax.post('api/room', { action: 'create', playerName: name, color: selectedColor }, function(data) {
                setLoading(btn, false);
                if (data.success) {
                    window.location.href = 'game.jsp?room=' + data.roomCode + '&player=' + encodeURIComponent(name) + '&color=' + encodeURIComponent(selectedColor) + '&guest=' + isGuest;
                } else {
                    showError(data.error || 'Failed to create room');
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

        function showError(msg) {
            var el = document.getElementById('errorMsg');
            el.textContent = msg;
            el.style.display = 'block';
            setTimeout(function() { el.style.display = 'none'; }, 3000);
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
