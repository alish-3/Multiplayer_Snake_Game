const Ajax = {
    _basePath: (function() {
        var path = window.location.pathname;
        var parts = path.split('/').filter(Boolean);
        if (parts.length > 1) {
            return '/' + parts[0];
        }
        return '';
    })(),

    _fullUrl: function(url) {
        // Ensure there's a slash between basePath and url
        if (this._basePath && !url.startsWith('/')) {
            return this._basePath + '/' + url;
        }
        return this._basePath + url;
    },

    get: function(url, callback) {
        callback = callback || function() {};
        const xhr = new XMLHttpRequest();
        xhr.open('GET', this._fullUrl(url), true);
        xhr.setRequestHeader('Accept', 'application/json');
        xhr.onreadystatechange = function() {
            if (xhr.readyState === 4) {
                if (xhr.status === 200) {
                    try {
                        callback(JSON.parse(xhr.responseText));
                    } catch (e) {
                        callback({ success: false, error: 'Invalid response' });
                    }
                } else {
                    callback({ success: false, error: 'Server error (' + xhr.status + ')' });
                }
            }
        };
        xhr.onerror = function() {
            callback({ success: false, error: 'Network error' });
        };
        xhr.send();
    },

    post: function(url, data, callback) {
        callback = callback || function() {};
        const xhr = new XMLHttpRequest();
        xhr.open('POST', this._fullUrl(url), true);
        xhr.setRequestHeader('Content-Type', 'application/json');
        xhr.setRequestHeader('Accept', 'application/json');
        xhr.onreadystatechange = function() {
            if (xhr.readyState === 4) {
                if (xhr.status === 200) {
                    try {
                        callback(JSON.parse(xhr.responseText));
                    } catch (e) {
                        callback({ success: false, error: 'Invalid response' });
                    }
                } else {
                    callback({ success: false, error: 'Server error (' + xhr.status + ')' });
                }
            }
        };
        xhr.onerror = function() {
            callback({ success: false, error: 'Network error' });
        };
        xhr.send(JSON.stringify(data));
    }
};
