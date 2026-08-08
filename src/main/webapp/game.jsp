<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%!
    private String esc(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder(s.length() + 16);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '&': sb.append("&amp;"); break;
                case '<': sb.append("&lt;"); break;
                case '>': sb.append("&gt;"); break;
                case '"': sb.append("&quot;"); break;
                case '\'': sb.append("&#39;"); break;
                default: sb.append(c);
            }
        }
        return sb.toString();
    }
%>
<%
    String room = request.getParameter("room");
    String player = request.getParameter("player");
    String color = request.getParameter("color");
    String guest = request.getParameter("guest");
    if (room == null || player == null) {
        response.sendRedirect("index.jsp");
        return;
    }
%>
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no, viewport-fit=cover">
    <meta name="mobile-web-app-capable" content="yes">
    <meta name="apple-mobile-web-app-capable" content="yes">
    <meta name="apple-mobile-web-app-status-bar-style" content="black-translucent">
    <link rel="manifest" href="manifest.webmanifest">
    <link rel="apple-touch-icon" href="icons/apple-touch-icon.png">
    <meta name="theme-color" content="#0d1021">
    <title>Snake Game - Room <%= esc(room) %></title>
    <link rel="icon" href="data:image/svg+xml,<svg xmlns=%22http://www.w3.org/2000/svg%22 viewBox=%220 0 100 100%22><text y=%22.9em%22 font-size=%2290%22>🐍</text></svg>">
    <link rel="stylesheet" href="css/style.css?v=3">
    <script>
        // Apply saved control scheme before first paint to avoid UI flash
        (function() {
            try {
                var saved = localStorage.getItem('snake_controlScheme');
                if (saved === 'swipe') {
                    document.documentElement.classList.add('swipe-mode');
                }
            } catch (e) {}
        })();
    </script>
</head>
<body class="game-page">

    <!-- Desktop header (hidden on <= 1024px) -->
    <header class="game-header desktop-header">
        <div class="header-left">
            <h2 data-i18n="appName">🐍 Multiplayer Snake</h2>
            <span class="room-code" id="roomCodeDisplay"><span data-i18n="room">Room:</span> <strong><%= esc(room) %></strong></span>
        </div>
        <div class="header-center">
            <span class="player-count"><span data-i18n="players">Players:</span> <span id="playerCountVal">-</span></span>
            <span class="ping-display"><span data-i18n="ping">Ping:</span> <span id="pingVal">-</span>ms</span>
        </div>
        <div class="header-right">
            <button class="icon-btn" id="copyRoomBtn" title="Copy Room Code" aria-label="Copy Room Code" data-i18n-aria="copyRoomCode">📋</button>
            <button class="icon-btn" id="fullscreenBtn" title="Fullscreen" aria-label="Toggle Fullscreen" data-i18n-aria="fullscreen">⛶</button>
            <button class="icon-btn" id="soundBtn" title="Sound" aria-label="Toggle Sound" data-i18n-aria="sound">🔊</button>
            <button class="icon-btn" id="settingsBtn" title="Settings" aria-label="Open Settings" data-i18n-aria="settings">⚙</button>
            <button id="langToggle" class="icon-btn lang-toggle" aria-label="Switch language" title="Switch language">🌐<span id="langToggleLabel">EN</span></button>
            <a class="btn btn-danger" href="index.jsp" data-i18n="leaveRoom">Leave Room</a>
        </div>
    </header>

    <!-- Mobile header (hidden on >= 1025px) -->
    <header class="game-header mobile-header">
        <div class="mobile-header-left">
            <span class="mobile-room" id="mobileRoomCode"><%= esc(room) %></span>
            <span class="mobile-score"><span data-i18n="score">L:</span> <span id="mobileScoreVal">0</span></span>
        </div>
        <div class="mobile-header-center">
            <span class="mobile-rank" id="mobileRank"><span data-i18n="rank">Rank</span> #-</span>
            <span class="mobile-alive"><span id="playersAliveVal">-</span> <span data-i18n="alive">alive</span></span>
            <span class="mobile-ping"><span id="mobilePingVal">-</span>ms</span>
        </div>
        <div class="mobile-header-right">
            <button class="icon-btn" id="mobileSoundBtn" title="Sound" aria-label="Toggle Sound" data-i18n-aria="sound">🔊</button>
            <button class="icon-btn" id="mobileFullscreenBtn" title="Fullscreen" aria-label="Toggle Fullscreen" data-i18n-aria="fullscreen">⛶</button>
            <button class="icon-btn" id="mobileSettingsBtn" title="Settings" aria-label="Open Settings" data-i18n-aria="settings">⚙</button>
            <button id="langToggleMobile" class="icon-btn lang-toggle" aria-label="Switch language" title="Switch language">🌐<span id="langToggleLabelMobile">EN</span></button>
            <a class="icon-btn" href="profile.jsp" title="Profile" aria-label="View Profile" data-i18n-aria="viewProfile">👤</a>
            <a class="icon-btn leave-btn" href="index.jsp" title="Leave Room" aria-label="Leave Room" data-i18n-aria="leaveRoom">✕</a>
        </div>
    </header>

    <div class="game-wrapper">
    <div class="game-canvas-wrapper">
        <canvas id="gameCanvas" width="600" height="600"></canvas>
        <!-- Swipe overlay: receives gestures in swipe mode -->
        <div class="touch-swipe-area" id="swipeArea" aria-hidden="true"></div>
        
        <!-- Bot Debug Info (visible during development) -->
        <div id="botDebugInfo" class="bot-debug-info" style="display: none;">
            <h3>Bot Debug Info</h3>
            <div id="botStatus"></div>
            <div id="botStats"></div>
        </div>
    </div>

        <!-- Sidebar / Leaderboard -->
        <aside class="sidebar" id="sidebar" aria-label="Leaderboard">
            <div class="sidebar-header">
                <div class="sidebar-header-row">
                    <h3 data-i18n="leaderboard">🏆 Leaderboard</h3>
                    <div class="sidebar-stats">
                        <span class="game-timer" id="gameTimer">⏱ <span id="timerVal">0:00</span></span>
                        <span class="current-rank" id="currentRank"><span data-i18n="rank">Rank</span> <span id="rankVal">-</span></span>
                    </div>
                    <button class="icon-btn scores-close" id="closeScoresBtn" title="Leaderboard" aria-label="Toggle Leaderboard" data-i18n-aria="leaderboard">▲</button>
                </div>
                <label class="control-scheme-toggle" id="controlSchemeToggle">
                    <input type="radio" name="controlScheme" id="controlSwipe" value="swipe">
                    <span class="toggle-label" data-i18n="swipeControls">👆 Swipe</span>
                    <input type="radio" name="controlScheme" id="controlDpad" value="dpad" checked>
                    <span class="toggle-label" data-i18n="dpadControls">🎮 D-Pad</span>
                </label>
            </div>
            <div id="scoreboard"></div>
            <div class="controls-info desktop-controls">
                <strong data-i18n="controls">Controls</strong><br>
                <kbd>&uarr;</kbd> <kbd>&darr;</kbd> <kbd>&larr;</kbd> <kbd>&rarr;</kbd> <span data-i18n="move">Move</span><br>
                <kbd>Space</kbd> / <kbd>Shift</kbd> <span data-i18n="boostHold">Boost (hold - eats your length)</span><br>
                <kbd>Enter</kbd> <span data-i18n="readyRestart">Ready / Restart</span>
            </div>
        </aside>
    </div>

    <!-- Mobile touch controls (D-Pad + READY + BOOST) -->
    <div class="touch-controls dpad-controls" id="dpadControls">
        <div class="touch-ready-area">
            <button class="touch-ready-btn" id="touchReadyBtn" data-i18n="ready">READY</button>
            <button class="touch-boost-btn" id="boostBtn" title="Hold to boost (consumes length)" data-i18n="boost">⚡ BOOST</button>
        </div>
        <div class="touch-dpad">
            <div class="dpad-row">
                <button class="dpad-btn" data-dir="UP" aria-label="Up" data-i18n-aria="up">▲</button>
            </div>
            <div class="dpad-row">
                <button class="dpad-btn" data-dir="LEFT" aria-label="Left" data-i18n-aria="left">◀</button>
                <button class="dpad-btn" data-dir="DOWN" aria-label="Down" data-i18n-aria="down">▼</button>
                <button class="dpad-btn" data-dir="RIGHT" aria-label="Right" data-i18n-aria="right">▶</button>
            </div>
        </div>
    </div>

    <div class="guest-bar" id="guestBar" data-i18n="guestBar" aria-live="polite" style="display:<%= "true".equals(guest) ? "block" : "none" %>;">
        ⚠️ Playing as Guest — your data will not be saved
    </div>

    <!-- Settings Modal -->
    <div class="modal-overlay" id="settingsModal" aria-hidden="true" role="dialog" aria-labelledby="settingsTitle" aria-modal="true">
        <div class="modal-content">
            <div class="modal-header">
                <h3 id="settingsTitle" data-i18n="settings">⚙ Settings</h3>
                <button class="modal-close" id="closeSettings" aria-label="Close Settings" data-i18n-aria="close">×</button>
            </div>
            <div class="modal-body">
                <div class="setting-group control-scheme-group">
                    <h4 data-i18n="controlScheme">Control Scheme</h4>
                    <div class="radio-group">
                        <label class="radio-label">
                            <input type="radio" name="controlSchemeModal" id="modalControlSwipe" value="swipe">
                            <span class="radio-custom"></span>
                            <span data-i18n="swipeControls">👆 Swipe Controls</span>
                            <span class="radio-desc" data-i18n="swipeDesc">Swipe on the game area to change direction</span>
                        </label>
                        <label class="radio-label">
                            <input type="radio" name="controlSchemeModal" id="modalControlDpad" value="dpad" checked>
                            <span class="radio-custom"></span>
                            <span data-i18n="dpadControls">🎮 D-Pad Controls</span>
                            <span class="radio-desc" data-i18n="dpadDesc">Use on-screen directional buttons</span>
                        </label>
                    </div>
                </div>
                <div class="setting-group">
                    <h4 data-i18n="sound">Sound</h4>
                    <label class="toggle-label">
                        <input type="checkbox" id="soundToggle" checked>
                        <span class="toggle-slider"></span>
                        <span data-i18n="gameSounds">Game Sounds</span>
                    </label>
                </div>
                <div class="setting-group">
                    <h4 data-i18n="display">Display</h4>
                    <label class="toggle-label">
                        <input type="checkbox" id="showGridToggle" checked>
                        <span class="toggle-slider"></span>
                        <span data-i18n="showGrid">Show Grid</span>
                    </label>
                    <label class="toggle-label">
                        <input type="checkbox" id="particlesToggle" checked>
                        <span class="toggle-slider"></span>
                        <span data-i18n="particleEffects">Particle Effects</span>
                    </label>
                </div>
                <div class="setting-group">
                    <h4 data-i18n="data">Data</h4>
                    <button class="btn btn-secondary" id="clearDataBtn" data-i18n="clearLocalData">Clear Local Data</button>
                </div>
                <div class="setting-group">
                    <h4 data-i18n="account">Account</h4>
                    <a href="profile.jsp" class="btn btn-primary" style="width: 100%;" data-i18n="viewProfile">👤 View Profile</a>
                </div>
            </div>
        </div>
    </div>

    <input type="hidden" id="playerName" value="<%= esc(player) %>">
    <input type="hidden" id="playerColor" value="<%= esc(color != null ? color : "#e94560") %>">
    <input type="hidden" id="isGuest" value="<%= "true".equals(guest) ? "true" : "false" %>">

    <script src="js/i18n.js"></script>
    <script src="js/ajax.js?v=3"></script>
    <script src="js/game.js?v=3"></script>
    <script>
    (function () {
      function updateLangLabels() {
        var isNe = window.I18n && I18n.getLang() === 'ne';
        var el = document.getElementById('langToggleLabel');
        var el2 = document.getElementById('langToggleLabelMobile');
        var label = isNe ? 'नेपाली' : 'EN';
        if (el) el.textContent = label;
        if (el2) el2.textContent = label;
      }
      function onClick() { if (window.I18n) I18n.toggleLang(); updateLangLabels(); }
      var btn = document.getElementById('langToggle');
      var btn2 = document.getElementById('langToggleMobile');
      if (btn) btn.addEventListener('click', onClick);
      if (btn2) btn2.addEventListener('click', onClick);
      if (window.I18n) {
        document.addEventListener('i18nchanged', updateLangLabels);
        if (document.readyState === 'loading') {
          document.addEventListener('DOMContentLoaded', updateLangLabels);
        } else {
          updateLangLabels();
        }
      }
    })();
    </script>
</body>
</html>
