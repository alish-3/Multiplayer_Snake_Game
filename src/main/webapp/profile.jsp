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
                case '"': sb.append("""); break;
                case '\'': sb.append("'"); break;
                default: sb.append(c);
            }
        }
        return sb.toString();
    }
%>
<%
    HttpSession session = request.getSession(false);
    boolean isGuest = session == null || Boolean.TRUE.equals(session.getAttribute("isGuest"));
    String username = isGuest ? null : (String) session.getAttribute("username");
    
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
    <title>Profile - Snake Game</title>
    <link rel="icon" href="data:image/svg+xml,<svg xmlns=%22http://www.w3.org/2000/svg%22 viewBox=%220 0 100 100%22><text y=%22.9em%22 font-size=%2290%22>🐍</text></svg>">
    <link rel="stylesheet" href="css/style.css?v=4">
</head>
<body class="lobby-page">
    <div class="lobby-container" id="app">
        <h1 class="game-title">🐍 My Profile</h1>

        <!-- Profile Card -->
        <div class="auth-card" id="profileCard">
            <!-- User Info Header -->
            <div class="profile-header">
                <div class="profile-avatar" id="profileAvatar"></div>
                <h2 class="profile-username" id="profileUsername"></h2>
                <span class="user-badge">✅ Registered User</span>
            </div>

            <div class="profile-divider"></div>

            <!-- Account Info -->
            <div class="profile-section">
                <h3 class="section-title">Account Information</h3>
                <div class="info-grid">
                    <div class="info-item">
                        <label class="info-label">Account Created</label>
                        <span class="info-value" id="createdAt"></span>
                    </div>
                    <div class="info-item">
                        <label class="info-label">Last Login</label>
                        <span class="info-value" id="lastLogin"></span>
                    </div>
                </div>
            </div>

            <!-- Stats -->
            <div class="profile-section">
                <h3 class="section-title">Game Statistics</h3>
                <div class="stats-grid">
                    <div class="stat-card">
                        <span class="stat-value" id="totalGames">0</span>
                        <span class="stat-label">Total Games</span>
                    </div>
                    <div class="stat-card">
                        <span class="stat-value" id="totalScore">0</span>
                        <span class="stat-label">Total Score</span>
                    </div>
                    <div class="stat-card">
                        <span class="stat-value" id="highScore">0</span>
                        <span class="stat-label">High Score</span>
                    </div>
                </div>
            </div>
        </div>

        <!-- Change Username Form -->
        <div class="auth-card" id="usernameCard">
            <h3 class="section-title">Change Username</h3>
            <form id="usernameForm" novalidate>
                <div class="form-group">
                    <label for="newUsername">New Username</label>
                    <input type="text" id="newUsername" name="newUsername" placeholder="New username" maxlength="20" autocomplete="username" required aria-describedby="usernameError">
                    <div class="form-error" id="usernameError" role="alert"></div>
                </div>
                <div class="form-group">
                    <label for="usernameCurrentPassword">Current Password</label>
                    <input type="password" id="usernameCurrentPassword" name="currentPassword" placeholder="Current password" maxlength="100" autocomplete="current-password" required aria-describedby="usernameError">
                    <div class="form-error" id="usernameError" role="alert"></div>
                </div>
                <button type="submit" class="btn btn-primary" id="usernameSubmitBtn">Update Username</button>
            </form>
        </div>

        <!-- Change Password Form -->
        <div class="auth-card" id="passwordCard">
            <h3 class="section-title">Change Password</h3>
            <form id="passwordForm" novalidate>
                <div class="form-group">
                    <label for="currentPassword">Current Password</label>
                    <input type="password" id="currentPassword" name="currentPassword" placeholder="Current password" maxlength="100" autocomplete="current-password" required aria-describedby="passwordError">
                    <div class="form-error" id="passwordError" role="alert"></div>
                </div>
                <div class="form-group">
                    <label for="newPassword">New Password</label>
                    <input type="password" id="newPassword" name="newPassword" placeholder="New password" maxlength="100" autocomplete="new-password" required aria-describedby="passwordError">
                    <div class="form-error" id="passwordError" role="alert"></div>
                </div>
                <div class="form-group">
                    <label for="confirmPassword">Confirm New Password</label>
                    <input type="password" id="confirmPassword" name="confirmPassword" placeholder="Confirm new password" maxlength="100" autocomplete="new-password" required aria-describedby="passwordError">
                    <div class="form-error" id="passwordError" role="alert"></div>
                </div>
                <button type="submit" class="btn btn-primary" id="passwordSubmitBtn">Update Password</button>
            </form>
        </div>

        <!-- Delete Account Form -->
        <div class="auth-card danger-card" id="deleteCard">
            <h3 class="section-title danger-title">⚠️ Delete Account</h3>
            <p class="danger-desc">This action is irreversible. All your game history, scores, and statistics will be permanently deleted.</p>
            <form id="deleteForm" novalidate>
                <div class="form-group">
                    <label for="deleteCurrentPassword">Current Password</label>
                    <input type="password" id="deleteCurrentPassword" name="currentPassword" placeholder="Current password to confirm" maxlength="100" autocomplete="current-password" required aria-describedby="deleteError">
                    <div class="form-error" id="deleteError" role="alert"></div>
                </div>
                <div class="form-group checkbox-group">
                    <label class="checkbox-label">
                        <input type="checkbox" id="deleteConfirm" name="confirmed" required>
                        <span class="checkbox-custom"></span>
                        <span>I understand this is irreversible and all my data will be permanently deleted</span>
                    </label>
                    <div class="form-error" id="deleteError" role="alert"></div>
                </div>
                <button type="submit" class="btn btn-danger" id="deleteSubmitBtn">Delete Account</button>
            </form>
        </div>

        <!-- Navigation -->
        <div class="lobby-footer">
            <a href="index.jsp" class="btn btn-secondary" id="backToLobbyBtn">Back to Lobby</a>
        </div>

        <div id="errorMsg" class="error-msg" style="display:none;"></div>

        <footer class="page-footer">🐍 Multiplayer Snake — have fun!</footer>
    </div>

    <script src="js/ajax.js?v=4"></script>
    <script src="js/profile.js?v=1"></script>
</body>
</html>