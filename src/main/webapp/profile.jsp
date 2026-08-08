<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%!
    private String esc(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder(s.length() + 16);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '&': sb.append("&"); break;
                case '<': sb.append("<"); break;
                case '>': sb.append(">"); break;
                case '"': sb.append("&quot;"); break;
                case '\'': sb.append("'"); break;
                default: sb.append(c);
            }
        }
        return sb.toString();
    }
%>
<%
    HttpSession sess = request.getSession(false);
    boolean isGuest = sess == null || Boolean.TRUE.equals(sess.getAttribute("isGuest"));
    String username = isGuest ? null : (String) sess.getAttribute("username");
    
    if (isGuest || username == null) {
        response.sendRedirect("index.jsp");
        return;
    }
%>
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
    <title>Profile - Snake Game</title>
    <link rel="icon" href="data:image/svg+xml,<svg xmlns=%22http://www.w3.org/2000/svg%22 viewBox=%220 0 100 100%22><text y=%22.9em%22 font-size=%2290%22>🐍</text></svg>">
    <link rel="stylesheet" href="css/style.css?v=4">
</head>
<body class="lobby-page">
    <div class="lobby-container" id="app">
        <button id="langToggle" class="lang-toggle" aria-label="Switch language" title="Switch language">🌐 <span id="langToggleLabel">EN</span></button>
        <h1 class="game-title" data-i18n="myProfile">🐍 My Profile</h1>

        <!-- Profile Card -->
        <div class="auth-card" id="profileCard">
            <!-- User Info Header -->
            <div class="profile-header">
                <div class="profile-avatar" id="profileAvatar"></div>
                <h2 class="profile-username" id="profileUsername"></h2>
                <span class="user-badge" data-i18n="registeredUser">✅ Registered User</span>
            </div>

            <div class="profile-divider"></div>

            <!-- Account Info -->
            <div class="profile-section">
                <h3 class="section-title" data-i18n="accountInfo">Account Information</h3>
                <div class="info-grid">
                    <div class="info-item">
                        <label class="info-label" data-i18n="accountCreated">Account Created</label>
                        <span class="info-value" id="createdAt"></span>
                    </div>
                    <div class="info-item">
                        <label class="info-label" data-i18n="lastLogin">Last Login</label>
                        <span class="info-value" id="lastLogin"></span>
                    </div>
                </div>
            </div>

            <!-- Stats -->
            <div class="profile-section">
                <h3 class="section-title" data-i18n="gameStats">Game Statistics</h3>
                <div class="stats-grid">
                    <div class="stat-card">
                        <span class="stat-value" id="totalGames">0</span>
                        <span class="stat-label" data-i18n="totalGames">Total Games</span>
                    </div>
                    <div class="stat-card">
                        <span class="stat-value" id="totalScore">0</span>
                        <span class="stat-label" data-i18n="totalScore">Total Score</span>
                    </div>
                    <div class="stat-card">
                        <span class="stat-value" id="highScore">0</span>
                        <span class="stat-label" data-i18n="highScore">High Score</span>
                    </div>
                </div>
            </div>
        </div>

        <!-- Change Username Form -->
        <div class="auth-card" id="usernameCard">
            <h3 class="section-title" data-i18n="changeUsername">Change Username</h3>
            <form id="usernameForm" novalidate>
                <div class="form-group">
                    <label for="newUsername" data-i18n="newUsername">New Username</label>
                    <input type="text" id="newUsername" name="newUsername" placeholder="New username" maxlength="20" autocomplete="username" required aria-describedby="usernameError" data-i18n-placeholder="newUsernamePh">
                    <div class="form-error" id="usernameError" role="alert" aria-live="polite"></div>
                </div>
                <div class="form-group">
                    <label for="usernameCurrentPassword" data-i18n="currentPassword">Current Password</label>
                    <input type="password" id="usernameCurrentPassword" name="currentPassword" placeholder="Current password" maxlength="100" autocomplete="current-password" required aria-describedby="usernameError2" data-i18n-placeholder="currentPassword">
                    <div class="form-error" id="usernameError2" role="alert" aria-live="polite"></div>
                </div>
                <button type="submit" class="btn btn-primary" id="usernameSubmitBtn" data-i18n="updateUsername">Update Username</button>
            </form>
        </div>

        <!-- Change Password Form -->
        <div class="auth-card" id="passwordCard">
            <h3 class="section-title" data-i18n="changePassword">Change Password</h3>
            <form id="passwordForm" novalidate>
                <div class="form-group">
                    <label for="currentPassword" data-i18n="currentPassword">Current Password</label>
                    <input type="password" id="currentPassword" name="currentPassword" placeholder="Current password" maxlength="100" autocomplete="current-password" required aria-describedby="passwordError" data-i18n-placeholder="currentPassword">
                    <div class="form-error" id="passwordError" role="alert" aria-live="polite"></div>
                </div>
                <div class="form-group">
                    <label for="newPassword" data-i18n="newPassword">New Password</label>
                    <input type="password" id="newPassword" name="newPassword" placeholder="New password" maxlength="100" autocomplete="new-password" required aria-describedby="passwordError2" data-i18n-placeholder="newPassword">
                    <div class="form-error" id="passwordError2" role="alert" aria-live="polite"></div>
                </div>
                <div class="form-group">
                    <label for="confirmPassword" data-i18n="confirmNewPassword">Confirm New Password</label>
                    <input type="password" id="confirmPassword" name="confirmPassword" placeholder="Confirm new password" maxlength="100" autocomplete="new-password" required aria-describedby="passwordError3" data-i18n-placeholder="confirmNewPassword">
                    <div class="form-error" id="passwordError3" role="alert" aria-live="polite"></div>
                </div>
                <button type="submit" class="btn btn-primary" id="passwordSubmitBtn" data-i18n="updatePassword">Update Password</button>
            </form>
        </div>

        <!-- Delete Account Form -->
        <div class="auth-card danger-card" id="deleteCard">
            <h3 class="section-title danger-title" data-i18n="deleteAccount">⚠️ Delete Account</h3>
            <p class="danger-desc" data-i18n="deleteAccountDesc">This action is irreversible. All your game history, scores, and statistics will be permanently deleted.</p>
            <form id="deleteForm" novalidate>
                <div class="form-group">
                    <label for="deleteCurrentPassword" data-i18n="currentPassword">Current Password</label>
                    <input type="password" id="deleteCurrentPassword" name="currentPassword" placeholder="Current password to confirm" maxlength="100" autocomplete="current-password" required aria-describedby="deleteError" data-i18n-placeholder="currentPassword">
                    <div class="form-error" id="deleteError" role="alert" aria-live="polite"></div>
                </div>
                <div class="form-group checkbox-group">
                    <label class="checkbox-label">
                        <input type="checkbox" id="deleteConfirm" name="confirmed" required aria-describedby="deleteError2">
                        <span class="checkbox-custom"></span>
                        <span data-i18n="deleteConfirmLabel">I understand this is irreversible and all my data will be permanently deleted</span>
                    </label>
                    <div class="form-error" id="deleteError2" role="alert" aria-live="polite"></div>
                </div>
                <button type="submit" class="btn btn-danger" id="deleteSubmitBtn" data-i18n="delete">Delete Account</button>
            </form>
        </div>

        <!-- Navigation -->
        <div class="lobby-footer">
            <a href="index.jsp" class="btn btn-secondary" id="backToLobbyBtn" data-i18n="backToLobby">Back to Lobby</a>
        </div>

        <div id="errorMsg" class="error-msg" style="display:none;"></div>

        <footer class="page-footer" data-i18n="footer">🐍 Multiplayer Snake — have fun!</footer>
    </div>

    <script src="js/i18n.js"></script>
    <script src="js/ajax.js?v=4"></script>
    <script src="js/profile.js?v=1"></script>

    <script>
    (function () {
      function updateLangLabel() {
        var isNe = window.I18n && I18n.getLang() === 'ne';
        var el = document.getElementById('langToggleLabel');
        if (el) el.textContent = isNe ? 'नेपाली' : 'EN';
        if (window.I18n) document.title = I18n.t('titleProfile');
      }
      function onClick() { if (window.I18n) I18n.toggleLang(); updateLangLabel(); }
      var btn = document.getElementById('langToggle');
      if (btn) btn.addEventListener('click', onClick);
      if (window.I18n) {
        document.addEventListener('i18nchanged', updateLangLabel);
        if (document.readyState === 'loading') {
          document.addEventListener('DOMContentLoaded', updateLangLabel);
        } else { updateLangLabel(); }
      }
    })();
    </script>
</body>
</html>