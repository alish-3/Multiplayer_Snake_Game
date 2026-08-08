// Profile page JavaScript

// Toast notification system
function showToast(message, isError = false) {
    // Remove existing toast
    var existing = document.querySelector('.game-toast');
    if (existing) existing.remove();

    var toast = document.createElement('div');
    toast.className = 'game-toast' + (isError ? ' toast-error' : '');
    toast.textContent = message;
    document.body.appendChild(toast);

    setTimeout(function() {
        toast.style.animation = 'toastIn 0.25s ease reverse';
        setTimeout(function() { toast.remove(); }, 250);
    }, 3000);
}

// Show form error
function showFormError(errorId, message) {
    var el = document.getElementById(errorId);
    if (el) {
        el.textContent = message;
        el.style.display = 'block';
    }
}

// Clear form error
function clearFormError(errorId) {
    var el = document.getElementById(errorId);
    if (el) {
        el.textContent = '';
        el.style.display = 'none';
    }
}

// Clear all errors in a form
function clearFormErrors(formId) {
    var form = document.getElementById(formId);
    if (!form) return;
    form.querySelectorAll('.form-error').forEach(function(el) {
        el.textContent = '';
        el.style.display = 'none';
    });
}

// Set button loading state
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

// Format date for display
function formatDate(dateStr) {
    if (!dateStr) return '-';
    try {
        var date = new Date(dateStr);
        var locale = (I18n.getLang() === 'ne' ? 'ne-NP' : 'en-US');
        return date.toLocaleDateString(locale, {
            year: 'numeric',
            month: 'long',
            day: 'numeric'
        });
    } catch (e) {
        return dateStr;
    }
}

// Load profile data
function loadProfile() {
    Ajax.get('api/profile', function(data) {
        if (data.success && data.profile) {
            var profile = data.profile;
            
            // Set username
            var usernameEl = document.getElementById('profileUsername');
            if (usernameEl) {
                usernameEl.textContent = profile.username || 'Unknown';
            }
            
            // Set avatar (first letter)
            var avatarEl = document.getElementById('profileAvatar');
            if (avatarEl && profile.username) {
                avatarEl.textContent = profile.username.charAt(0).toUpperCase();
            }
            
            // Set dates
            var createdEl = document.getElementById('createdAt');
            if (createdEl) {
                createdEl.textContent = formatDate(profile.createdAt);
            }
            
            var loginEl = document.getElementById('lastLogin');
            if (loginEl) {
                loginEl.textContent = formatDate(profile.lastLogin);
            }
            
            // Set stats
            var gamesEl = document.getElementById('totalGames');
            if (gamesEl) {
                gamesEl.textContent = profile.totalGames || 0;
            }
            
            var scoreEl = document.getElementById('totalScore');
            if (scoreEl) {
                scoreEl.textContent = profile.totalScore || 0;
            }
            
            var highScoreEl = document.getElementById('highScore');
            if (highScoreEl) {
                highScoreEl.textContent = profile.highScore || 0;
            }
        } else {
            showToast(data.error || I18n.t('failedLoadProfile'), true);
        }
    });
}

// Handle username change form
function initUsernameForm() {
    var form = document.getElementById('usernameForm');
    if (!form) return;

    form.addEventListener('submit', function(e) {
        e.preventDefault();
        clearFormErrors('usernameForm');
        
        var newUsername = document.getElementById('newUsername').value.trim();
        var currentPassword = document.getElementById('usernameCurrentPassword').value;
        var btn = document.getElementById('usernameSubmitBtn');
        
        if (!newUsername) {
            showFormError('usernameError', I18n.t('newUsernameRequired'));
            return;
        }
        
        if (!/^[A-Za-z0-9_-]{3,20}$/.test(newUsername)) {
            showFormError('usernameError', I18n.t('usernameChars'));
            return;
        }
        
        if (!currentPassword) {
            showFormError('usernameError', I18n.t('currentPasswordRequired'));
            return;
        }
        
        setLoading(btn, true);
        
        Ajax.post('api/profile', {
            action: 'changeUsername',
            newUsername: newUsername,
            currentPassword: currentPassword
        }, function(data) {
            setLoading(btn, false);
            if (data.success) {
                showToast(I18n.t('usernameUpdated'));
                document.getElementById('newUsername').value = '';
                document.getElementById('usernameCurrentPassword').value = '';
                // Update displayed username
                var usernameEl = document.getElementById('profileUsername');
                if (usernameEl) usernameEl.textContent = data.username;
                var avatarEl = document.getElementById('profileAvatar');
                if (avatarEl) avatarEl.textContent = data.username.charAt(0).toUpperCase();
            } else {
                showFormError('usernameError', data.error || I18n.t('failedUpdateUsername'));
            }
        });
    });
}

// Handle password change form
function initPasswordForm() {
    var form = document.getElementById('passwordForm');
    if (!form) return;

    form.addEventListener('submit', function(e) {
        e.preventDefault();
        clearFormErrors('passwordForm');
        
        var currentPassword = document.getElementById('currentPassword').value;
        var newPassword = document.getElementById('newPassword').value;
        var confirmPassword = document.getElementById('confirmPassword').value;
        var btn = document.getElementById('passwordSubmitBtn');
        
        if (!currentPassword) {
            showFormError('passwordError', I18n.t('currentPasswordRequired'));
            return;
        }
        
        if (!newPassword) {
            showFormError('passwordError', I18n.t('newPasswordRequired'));
            return;
        }
        
        if (newPassword.length < 4 || newPassword.length > 100) {
            showFormError('passwordError', I18n.t('passwordLen'));
            return;
        }
        
        if (newPassword !== confirmPassword) {
            showFormError('passwordError', I18n.t('newPasswordsDontMatch'));
            return;
        }
        
        if (currentPassword === newPassword) {
            showFormError('passwordError', I18n.t('passwordSame'));
            return;
        }
        
        setLoading(btn, true);
        
        Ajax.post('api/profile', {
            action: 'changePassword',
            currentPassword: currentPassword,
            newPassword: newPassword,
            confirmPassword: confirmPassword
        }, function(data) {
            setLoading(btn, false);
            if (data.success) {
                showToast(I18n.t('passwordUpdated'));
                form.reset();
            } else {
                showFormError('passwordError', data.error || I18n.t('failedUpdatePassword'));
            }
        });
    });
}

// Handle delete account form
function initDeleteForm() {
    var form = document.getElementById('deleteForm');
    if (!form) return;

    form.addEventListener('submit', function(e) {
        e.preventDefault();
        clearFormErrors('deleteForm');
        
        var currentPassword = document.getElementById('deleteCurrentPassword').value;
        var confirmed = document.getElementById('deleteConfirm').checked;
        var btn = document.getElementById('deleteSubmitBtn');
        
        if (!currentPassword) {
            showFormError('deleteError', I18n.t('currentPasswordRequired'));
            return;
        }
        
        if (!confirmed) {
            showFormError('deleteError', I18n.t('mustUnderstand'));
            return;
        }
        
        // Double confirmation
        if (!confirm(I18n.t('confirmDeleteTitle'))) {
            return;
        }
        
        setLoading(btn, true);
        
        Ajax.post('api/profile', {
            action: 'deleteAccount',
            currentPassword: currentPassword,
            confirmed: true
        }, function(data) {
            setLoading(btn, false);
            if (data.success) {
                showToast(I18n.t('accountDeleted'));
                setTimeout(function() {
                    window.location.href = 'index.jsp';
                }, 1500);
            } else {
                showFormError('deleteError', data.error || I18n.t('failedDeleteAccount'));
            }
        });
    });
}

// Initialize on DOM ready
document.addEventListener('DOMContentLoaded', function() {
    loadProfile();
    initUsernameForm();
    initPasswordForm();
    initDeleteForm();
});