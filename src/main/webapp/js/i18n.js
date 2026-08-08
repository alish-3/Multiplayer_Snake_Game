/**
 * i18n.js — Tiny, dependency-free internationalization (i18n) module
 * for the Multiplayer Snake Game.
 *
 * Provides a minimal global `I18n` API for translating UI strings between
 * English ('en') and Nepali ('ne') — no frameworks, no build step, just
 * plain ES5+ JavaScript (the project uses vanilla XMLHttpRequest/WebSocket).
 *
 * Public API:
 *   - I18n.t(key)        → translated string for the current language,
 *                          falling back to English, then to the key itself.
 *   - I18n.setLang(lang) → switch language ('en' | 'ne'), persist it in
 *                          localStorage['snake_lang'], set <html lang>,
 *                          re-translate the DOM and dispatch an 'i18nchanged'
 *                          custom event so other scripts can update dynamic text.
 *   - I18n.getLang()     → current language ('en' or 'ne').
 *   - I18n.applyDom()    → translate every element that declares a
 *                          data-i18n / data-i18n-placeholder / data-i18n-aria
 *                          attribute.
 *   - I18n.toggleLang()  → cycle en → ne → en and return the new language
 *                          (intended for a header language button).
 *
 * Pages mark translatable elements declaratively:
 *   <button data-i18n="save">Save</button>
 *   <input data-i18n-placeholder="enterUsername" data-i18n-aria="username">
 *   <p data-i18n="modeHintFriends" data-i18n-html>...</p>   ← inline HTML allowed
 *
 * Text is applied with textContent by default (safe for labels/buttons);
 * add the data-i18n-html attribute to opt into innerHTML where the
 * translation legitimately contains inline HTML (e.g. the mode hints).
 *
 * On load the module reads localStorage['snake_lang']; if absent it detects
 * the language from navigator.language ('ne*' → Nepali, otherwise English).
 * On DOMContentLoaded, applyDom() runs automatically, but only when the page
 * actually contains [data-i18n] elements — pages that don't use the module
 * are untouched.
 *
 * Language level: ES5+ (var/function style). Only the top-level I18N_DICT
 * uses `const`, which every browser that supports WebSocket supports.
 */

/**
 * The translation dictionary. Every key exists in BOTH languages so pages
 * can reference keys without language-specific branching. Nepali strings use
 * natural, conversational Nepali (Devanagari script, respectful form) with
 * common loanwords (पासवर्ड, सेटिङ, कोठा, बट, स्कोर) and Nepali digits ०-९.
 */
const I18N_DICT = {
    en: {
        // Auth / account
        appName: '🐍 Multiplayer Snake',
        guest: 'Guest',
        login: 'Login',
        register: 'Register',
        logout: 'Logout',
        back: 'Back',
        close: 'Close',
        join: 'Join',
        create: 'Create',
        save: 'Save',
        cancel: 'Cancel',
        delete: 'Delete',
        username: 'Username',
        password: 'Password',
        confirmPassword: 'Confirm Password',
        newPassword: 'New Password',
        currentPassword: 'Current Password',
        rememberMe: 'Remember me (30 days)',
        createAccount: 'Create Account',
        enterUsername: 'Enter username',
        enterPassword: 'Enter password',
        chooseUsername: 'Choose a username',
        choosePassword: 'Choose a password',
        confirmPasswordPh: 'Confirm password',
        guestDesc: 'Play without an account. Your scores and progress will not be saved.',
        guestNotice: '⚠️ Your data will not be saved as a guest!',
        playAsGuest: 'Play as Guest',
        noAccount: 'Don\'t have an account?',
        registerHere: 'Register here',
        hasAccount: 'Already have an account?',
        loginHere: 'Login here',

        // Lobby / mode selection
        yourName: 'Your Name',
        yourNamePh: 'Enter your name...',
        yourSnakeColor: 'Your Snake Color',
        chooseHowToPlay: 'Choose How to Play',
        playWithFriends: 'Play with Friends',
        friendsDesc: 'Create or join rooms with other players. Share room codes and play together!',
        multiplayer: 'Multiplayer',
        players2to4: '2–4 Players',
        playWithBots: 'Play with Bots',
        botsDesc: 'Practice against AI opponents. Choose difficulty and number of bots.',
        singlePlayer: 'Single Player',
        bots1to3: '1–3 Bots',
        modeHintFriends: 'Click a mode to select — <strong>Play with Friends</strong> lets you create or join multiplayer rooms',
        modeHintBots: 'Click a mode to select — <strong>Play with Bots</strong> lets you practice against AI with adjustable difficulty',
        createNewRoom: 'Create New Room',
        customSettings: '⚙️ Custom Settings',
        roomCode: 'Room code',
        botSettings: 'Bot Settings',
        numberOfBots: 'Number of Bots',
        bot1: '1 Bot',
        bot2: '2 Bots',
        bot3: '3 Bots',
        difficulty: 'Difficulty',
        easy: '😊 Easy',
        normal: '😐 Normal',
        hard: '😤 Hard',
        impossible: '💀 Impossible',
        easyDesc: 'Easy bots move randomly and make frequent mistakes. Good for practice.',
        normalDesc: 'Normal bots play competitively but make occasional mistakes.',
        hardDesc: 'Hard bots are aggressive, predict your moves, and rarely make errors.',
        impossibleDesc: 'Impossible bots have perfect reflexes, predict all outcomes, and never make mistakes. You cannot win.',
        createBotRoom: 'Create Bot Room',
        activeRooms: 'Active Rooms',
        noActiveRooms: 'No active rooms. Create one!',
        customRoomSettings: 'Custom Room Settings',
        gameplay: 'Gameplay',
        gridSize: 'Grid Size',
        tickRateMs: 'Tick Rate (ms)',
        maxPlayers: 'Max Players',
        foodDensity: 'Food Density',
        features: 'Features',
        enableBoost: 'Enable Boost',
        enableGoldenFood: 'Enable Golden Food',
        resetToDefaults: 'Reset to Defaults',
        applySettings: 'Apply Settings',

        // Validation / errors
        fillAllFields: 'Fill in all fields',
        passwordsDontMatch: 'Passwords do not match',
        passwordMin4: 'Password must be at least 4 characters',
        loginFailed: 'Login failed',
        registrationFailed: 'Registration failed',
        usernameTaken: 'Username already taken',
        enterNameFirst: 'Enter your name first',
        failedCreateRoom: 'Failed to create room',
        failedCreateBotRoom: 'Failed to create bot room',
        enterRoomCode: 'Enter a room code',
        failedJoinRoom: 'Failed to join room',
        settingsApplied: 'Custom settings applied! Click "Create New Room" to create a room with these settings.',
        settingsReset: 'Settings reset to defaults.',
        viewProfile: 'View Profile',
        pleaseWait: 'Please wait...',

        // In-game UI
        room: 'Room',
        players: 'Players',
        ping: 'Ping',
        copyRoomCode: 'Copy Room Code',
        fullscreen: 'Fullscreen',
        sound: 'Sound',
        settings: 'Settings',
        leaveRoom: 'Leave Room',
        score: 'Score',
        rank: 'Rank',
        alive: 'alive',
        leaderboard: '🏆 Leaderboard',
        controls: 'Controls',
        move: 'Move',
        boostHold: 'Boost (hold - eats your length)',
        readyRestart: 'Ready / Restart',
        ready: 'READY',
        boost: '⚡ BOOST',
        up: 'Up',
        down: 'Down',
        left: 'Left',
        right: 'Right',
        guestBar: '⚠️ Playing as Guest — your data will not be saved',
        controlScheme: 'Control Scheme',
        swipeControls: '👆 Swipe Controls',
        swipeDesc: 'Swipe on the game area to change direction',
        dpadControls: '🎮 D-Pad Controls',
        dpadDesc: 'Use on-screen directional buttons',
        gameSounds: 'Game Sounds',
        display: 'Display',
        showGrid: 'Show Grid',
        particleEffects: 'Particle Effects',
        data: 'Data',
        clearLocalData: 'Clear Local Data',
        account: 'Account',
        gameOver: 'GAME OVER',
        time: 'Time',
        waitingForPlayers: 'Waiting for players...',
        readyWaiting: 'Ready! Waiting for others...',
        tapReadyRestart: 'Tap READY to restart',
        pressSpaceReady: 'Press SPACE to ready up',
        tapReadyWhenReady: 'Tap READY when ready',
        pressSpaceWhenReady: 'Press SPACE when ready',
        notReadyYet: 'Not ready yet',
        reconnecting: 'Reconnecting...',
        connecting: 'Connecting...',
        swipeEnabled: 'Swipe controls enabled',
        dpadEnabled: 'D-Pad controls enabled',
        swipeActive: 'Swipe controls active',
        dpadActive: 'D-Pad controls active',
        soundOn: 'Sound on',
        soundOff: 'Sound off',
        fullscreenUnavailable: 'Fullscreen unavailable',
        roomCodeCopied: 'Room code copied',
        localDataCleared: 'Local data cleared',
        failedJoin: 'Failed to join',

        // Profile / account management
        titleProfile: 'Profile - Snake Game',
        myProfile: '🐍 My Profile',
        registeredUser: '✅ Registered User',
        accountInfo: 'Account Information',
        accountCreated: 'Account Created',
        lastLogin: 'Last Login',
        gameStats: 'Game Statistics',
        totalGames: 'Total Games',
        totalScore: 'Total Score',
        highScore: 'High Score',
        changeUsername: 'Change Username',
        newUsername: 'New Username',
        newUsernamePh: 'New username',
        updateUsername: 'Update Username',
        changePassword: 'Change Password',
        confirmNewPassword: 'Confirm New Password',
        updatePassword: 'Update Password',
        deleteAccount: '⚠️ Delete Account',
        deleteAccountDesc: 'This action is irreversible. All your game history, scores, and statistics will be permanently deleted.',
        deleteConfirmLabel: 'I understand this is irreversible and all my data will be permanently deleted',
        backToLobby: 'Back to Lobby',
        footer: '🐍 Multiplayer Snake — have fun!',

        // Feedback / misc
        usernameUpdated: 'Username updated successfully!',
        passwordUpdated: 'Password updated successfully!',
        accountDeleted: 'Account deleted successfully',
        failedLoadProfile: 'Failed to load profile',
        failedUpdateUsername: 'Failed to update username',
        failedUpdatePassword: 'Failed to update password',
        failedDeleteAccount: 'Failed to delete account',
        networkError: 'Network error',
        serverError: 'Server error',
        invalidResponse: 'Invalid response',
        confirmDeleteTitle: 'Are you absolutely sure you want to delete your account? This cannot be undone.',
        mustUnderstand: 'You must confirm that you understand this is irreversible',
        newUsernameRequired: 'Please enter a new username',
        usernameChars: 'Username must be 3-20 characters (letters, numbers, underscore, hyphen)',
        currentPasswordRequired: 'Please enter your current password',
        newPasswordRequired: 'Please enter a new password',
        passwordLen: 'Password must be between 4 and 100 characters',
        newPasswordsDontMatch: 'New passwords do not match',
        passwordSame: 'New password must be different from current password'
    },

    ne: {
        // Auth / account
        appName: '🐍 मल्टिप्लेयर सर्प',
        guest: 'पाहुना',
        login: 'प्रवेश गर्नुहोस्',
        register: 'दर्ता गर्नुहोस्',
        logout: 'बाहिर जानुहोस्',
        back: 'पछाडि',
        close: 'बन्द गर्नुहोस्',
        join: 'सामेल हुनुहोस्',
        create: 'बनाउनुहोस्',
        save: 'सुरक्षित गर्नुहोस्',
        cancel: 'रद्द गर्नुहोस्',
        delete: 'मेटाउनुहोस्',
        username: 'प्रयोगकर्ता नाम',
        password: 'पासवर्ड',
        confirmPassword: 'पासवर्ड पुष्टि गर्नुहोस्',
        newPassword: 'नयाँ पासवर्ड',
        currentPassword: 'हालको पासवर्ड',
        rememberMe: 'मलाई सम्झनुहोस् (३० दिन)',
        createAccount: 'खाता बनाउनुहोस्',
        enterUsername: 'प्रयोगकर्ता नाम लेख्नुहोस्',
        enterPassword: 'पासवर्ड लेख्नुहोस्',
        chooseUsername: 'प्रयोगकर्ता नाम छान्नुहोस्',
        choosePassword: 'पासवर्ड छान्नुहोस्',
        confirmPasswordPh: 'पासवर्ड फेरि लेख्नुहोस्',
        guestDesc: 'खाता बिना पनि खेल्न सकिन्छ। तर स्कोर र प्रगति सुरक्षित हुँदैन।',
        guestNotice: '⚠️ पाहुनाको रूपमा तपाईंको डेटा सुरक्षित हुँदैन!',
        playAsGuest: 'पाहुनाको रूपमा खेल्नुहोस्',
        noAccount: 'खाता छैन?',
        registerHere: 'यहाँ दर्ता गर्नुहोस्',
        hasAccount: 'खाता छ?',
        loginHere: 'यहाँ प्रवेश गर्नुहोस्',

        // Lobby / mode selection
        yourName: 'तपाईंको नाम',
        yourNamePh: 'आफ्नो नाम लेख्नुहोस्...',
        yourSnakeColor: 'तपाईंको सर्पको रङ',
        chooseHowToPlay: 'कसरी खेल्ने छान्नुहोस्',
        playWithFriends: 'साथीहरूसँग खेल्नुहोस्',
        friendsDesc: 'अरू खेलाडीसँग कोठा बनाउनुहोस् वा सामेल हुनुहोस्। कोठा कोड सेयर गरेर सँगै खेल्नुहोस्!',
        multiplayer: 'मल्टिप्लेयर',
        players2to4: '२–४ खेलाडी',
        playWithBots: 'बटहरूसँग खेल्नुहोस्',
        botsDesc: 'AI प्रतिद्वन्द्वीसँग अभ्यास गर्नुहोस्। कठिनाइ र बटको सङ्ख्या छान्नुहोस्।',
        singlePlayer: 'एकल खेलाडी',
        bots1to3: '१–३ बटहरू',
        modeHintFriends: 'खेल्ने तरिका छान्नुहोस् — <strong>साथीहरूसँग खेल्नुहोस्</strong> मा मल्टिप्लेयर कोठा बनाउन वा सामेल हुन सकिन्छ',
        modeHintBots: 'खेल्ने तरिका छान्नुहोस् — <strong>बटहरूसँग खेल्नुहोस्</strong> मा मनपर्ने कठिनाइमा AI सँग अभ्यास गर्न सकिन्छ',
        createNewRoom: 'नयाँ कोठा बनाउनुहोस्',
        customSettings: '⚙️ आफ्नै सेटिङहरू',
        roomCode: 'कोठा कोड',
        botSettings: 'बट सेटिङ',
        numberOfBots: 'बटको सङ्ख्या',
        bot1: '१ बट',
        bot2: '२ बटहरू',
        bot3: '३ बटहरू',
        difficulty: 'कठिनाइ',
        easy: '😊 सजिलो',
        normal: '😐 सामान्य',
        hard: '😤 कठिन',
        impossible: '💀 असम्भव',
        easyDesc: 'सजिलो बटहरू अनियमित चल्छन् र बारम्बार गल्ती गर्छन्। अभ्यासका लागि राम्रो।',
        normalDesc: 'सामान्य बटहरू प्रतिस्पर्धात्मक खेल्छन् तर कहिलेकाहीँ गल्ती गर्छन्।',
        hardDesc: 'कठिन बटहरू आक्रामक हुन्छन्, तपाईंको चाल अड्कल्छन् र कमै गल्ती गर्छन्।',
        impossibleDesc: 'असम्भव बटहरूमा पूर्ण रिफ्लेक्स हुन्छ, सबै नतिजा अड्कल्छन् र कहिल्यै गल्ती गर्दैनन्। जित्न सकिँदैन।',
        createBotRoom: 'बट कोठा बनाउनुहोस्',
        activeRooms: 'सक्रिय कोठाहरू',
        noActiveRooms: 'कुनै सक्रिय कोठा छैन। एउटा बनाउनुहोस्!',
        customRoomSettings: 'आफ्नै कोठा सेटिङ',
        gameplay: 'खेल',
        gridSize: 'ग्रिड आकार',
        tickRateMs: 'टिक दर (मि.से.)',
        maxPlayers: 'अधिकतम खेलाडी',
        foodDensity: 'खानाको घनत्व',
        features: 'सुविधाहरू',
        enableBoost: 'बुस्ट सक्रिय गर्नुहोस्',
        enableGoldenFood: 'सुनौलो खाना सक्रिय गर्नुहोस्',
        resetToDefaults: 'सुरुकै सेटिङमा फर्काउनुहोस्',
        applySettings: 'सेटिङ लागू गर्नुहोस्',

        // Validation / errors
        fillAllFields: 'सबै फाँट भर्नुहोस्',
        passwordsDontMatch: 'पासवर्डहरू मिलेनन्',
        passwordMin4: 'पासवर्ड कम्तीमा ४ अक्षरको हुनुपर्छ',
        loginFailed: 'प्रवेश असफल भयो',
        registrationFailed: 'दर्ता असफल भयो',
        usernameTaken: 'प्रयोगकर्ता नाम पहिले नै लिइएको छ',
        enterNameFirst: 'पहिले आफ्नो नाम लेख्नुहोस्',
        failedCreateRoom: 'कोठा बनाउन असफल भयो',
        failedCreateBotRoom: 'बट कोठा बनाउन असफल भयो',
        enterRoomCode: 'कोठा कोड लेख्नुहोस्',
        failedJoinRoom: 'कोठामा सामेल हुन असफल भयो',
        settingsApplied: 'सेटिङ लागू भयो! यी सेटिङसहित कोठा बनाउन "नयाँ कोठा बनाउनुहोस्" थिच्नुहोस्।',
        settingsReset: 'सेटिङ सुरुकै अवस्थामा फर्कियो।',
        viewProfile: 'प्रोफाइल हेर्नुहोस्',
        pleaseWait: 'कृपया पर्खनुहोस्...',

        // In-game UI
        room: 'कोठा',
        players: 'खेलाडीहरू',
        ping: 'पिङ',
        copyRoomCode: 'कोठा कोड प्रतिलिपि गर्नुहोस्',
        fullscreen: 'पूर्ण स्क्रिन',
        sound: 'आवाज',
        settings: 'सेटिङ',
        leaveRoom: 'कोठा छोड्नुहोस्',
        score: 'स्कोर',
        rank: 'स्थान',
        alive: 'जीवित',
        leaderboard: '🏆 अग्रता सूची',
        controls: 'नियन्त्रण',
        move: 'सार्नुहोस्',
        boostHold: 'बुस्ट (थिचिराख्नुहोस् - लम्बाइ खान्छ)',
        readyRestart: 'तयार / पुनः सुरु',
        ready: 'तयार',
        boost: '⚡ बुस्ट',
        up: 'माथि',
        down: 'तल',
        left: 'बायाँ',
        right: 'दायाँ',
        guestBar: '⚠️ पाहुनाको रूपमा खेल्दै हुनुहुन्छ — तपाईंको डेटा सुरक्षित हुँदैन',
        controlScheme: 'नियन्त्रण विधि',
        swipeControls: '👆 स्वाइप नियन्त्रण',
        swipeDesc: 'खेल क्षेत्रमा स्वाइप गरेर दिशा बदल्नुहोस्',
        dpadControls: '🎮 डि-प्याड नियन्त्रण',
        dpadDesc: 'स्क्रिनका दिशा बटनहरू प्रयोग गर्नुहोस्',
        gameSounds: 'खेलको आवाज',
        display: 'प्रदर्शन',
        showGrid: 'ग्रिड देखाउनुहोस्',
        particleEffects: 'कण प्रभावहरू',
        data: 'डेटा',
        clearLocalData: 'स्थानीय डेटा मेटाउनुहोस्',
        account: 'खाता',
        gameOver: 'खेल सकियो',
        time: 'समय',
        waitingForPlayers: 'खेलाडीहरूको पर्खाइमा...',
        readyWaiting: 'तयार! अरूको पर्खाइमा...',
        tapReadyRestart: 'पुनः सुरु गर्न तयार थिच्नुहोस्',
        pressSpaceReady: 'तयार हुन स्पेस थिच्नुहोस्',
        tapReadyWhenReady: 'तयार हुँदा तयार थिच्नुहोस्',
        pressSpaceWhenReady: 'तयार हुँदा स्पेस थिच्नुहोस्',
        notReadyYet: 'अझै तयार हुनुभएको छैन',
        reconnecting: 'पुनः जडान हुँदैछ...',
        connecting: 'जडान हुँदैछ...',
        swipeEnabled: 'स्वाइप नियन्त्रण सक्रिय भयो',
        dpadEnabled: 'डि-प्याड नियन्त्रण सक्रिय भयो',
        swipeActive: 'स्वाइप नियन्त्रण सक्रिय छ',
        dpadActive: 'डि-प्याड नियन्त्रण सक्रिय छ',
        soundOn: 'आवाज खुल्यो',
        soundOff: 'आवाज बन्द भयो',
        fullscreenUnavailable: 'पूर्ण स्क्रिन उपलब्ध छैन',
        roomCodeCopied: 'कोठा कोड प्रतिलिपि भयो',
        localDataCleared: 'स्थानीय डेटा मेटियो',
        failedJoin: 'सामेल हुन असफल भयो',

        // Profile / account management
        titleProfile: 'प्रोफाइल - सर्प खेल',
        myProfile: '🐍 मेरो प्रोफाइल',
        registeredUser: '✅ दर्ता गरिएको प्रयोगकर्ता',
        accountInfo: 'खाता जानकारी',
        accountCreated: 'खाता खुलेको मिति',
        lastLogin: 'पछिल्लो प्रवेश',
        gameStats: 'खेल तथ्याङ्क',
        totalGames: 'कुल खेलहरू',
        totalScore: 'कुल स्कोर',
        highScore: 'उच्च स्कोर',
        changeUsername: 'प्रयोगकर्ता नाम बदल्नुहोस्',
        newUsername: 'नयाँ प्रयोगकर्ता नाम',
        newUsernamePh: 'नयाँ प्रयोगकर्ता नाम लेख्नुहोस्',
        updateUsername: 'प्रयोगकर्ता नाम अद्यावधिक गर्नुहोस्',
        changePassword: 'पासवर्ड बदल्नुहोस्',
        confirmNewPassword: 'नयाँ पासवर्ड पुष्टि गर्नुहोस्',
        updatePassword: 'पासवर्ड अद्यावधिक गर्नुहोस्',
        deleteAccount: '⚠️ खाता मेटाउनुहोस्',
        deleteAccountDesc: 'यो कार्य रद्द गर्न सकिँदैन। तपाईंको सबै खेल इतिहास, स्कोर र तथ्याङ्क सधैंका लागि मेटिनेछन्।',
        deleteConfirmLabel: 'म बुझ्दछु — यो रद्द गर्न सकिँदैन र मेरो सबै डेटा सधैंका लागि मेटिनेछ',
        backToLobby: 'लबीमा फर्कनुहोस्',
        footer: '🐍 मल्टिप्लेयर सर्प — रमाइलो गर्नुहोस्!',

        // Feedback / misc
        usernameUpdated: 'प्रयोगकर्ता नाम सफलतापूर्वक अद्यावधिक भयो!',
        passwordUpdated: 'पासवर्ड सफलतापूर्वक अद्यावधिक भयो!',
        accountDeleted: 'खाता सफलतापूर्वक मेटियो',
        failedLoadProfile: 'प्रोफाइल लोड गर्न असफल भयो',
        failedUpdateUsername: 'प्रयोगकर्ता नाम अद्यावधिक गर्न असफल भयो',
        failedUpdatePassword: 'पासवर्ड अद्यावधिक गर्न असफल भयो',
        failedDeleteAccount: 'खाता मेटाउन असफल भयो',
        networkError: 'नेटवर्क समस्या',
        serverError: 'सर्भर समस्या',
        invalidResponse: 'गलत प्रतिक्रिया',
        confirmDeleteTitle: 'के तपाईं साँच्चै आफ्नो खाता मेटाउन चाहनुहुन्छ? यो फेरि फर्काउन सकिँदैन।',
        mustUnderstand: 'तपाईंले यो अपरिवर्तनीय हो भन्ने कुरा पुष्टि गर्नुपर्छ',
        newUsernameRequired: 'कृपया नयाँ प्रयोगकर्ता नाम लेख्नुहोस्',
        usernameChars: 'प्रयोगकर्ता नाम ३–२० अक्षरको हुनुपर्छ (अक्षर, अङ्क, अन्डरस्कोर, हाइफन)',
        currentPasswordRequired: 'कृपया हालको पासवर्ड लेख्नुहोस्',
        newPasswordRequired: 'कृपया नयाँ पासवर्ड लेख्नुहोस्',
        passwordLen: 'पासवर्ड ४–१०० अक्षरको हुनुपर्छ',
        newPasswordsDontMatch: 'नयाँ पासवर्डहरू मिलेनन्',
        passwordSame: 'नयाँ पासवर्ड हालको भन्दा फरक हुनुपर्छ'
    }
};

/**
 * I18n singleton — the public API. Implemented with ES5-friendly
 * var/function style (no arrow functions, no this-binding pitfalls).
 */
var I18n = (function() {
    'use strict';

    var STORAGE_KEY = 'snake_lang';
    var DEFAULT_LANG = 'en';

    /**
     * Resolve the initial language:
     * 1. localStorage['snake_lang'] (previously persisted choice), else
     * 2. navigator.language (startswith 'ne' → Nepali), else
     * 3. English.
     */
    function getInitialLang() {
        var stored = null;
        try {
            stored = localStorage.getItem(STORAGE_KEY);
        } catch (e) {
            stored = null;
        }
        if (typeof stored === 'string' && hasKey(I18N_DICT, stored)) {
            return stored;
        }
        var nav = (navigator.language || navigator.userLanguage || '').toLowerCase();
        if (nav.indexOf('ne') === 0) {
            return 'ne';
        }
        return DEFAULT_LANG;
    }

    /** Safe hasOwnProperty check (works for keys like 'toString'). */
    function hasKey(obj, key) {
        return Object.prototype.hasOwnProperty.call(obj, key);
    }

    /** Normalize any input to a supported language code ('en' | 'ne'). */
    function normalizeLang(lang) {
        return (typeof lang === 'string' && hasKey(I18N_DICT, lang)) ? lang : DEFAULT_LANG;
    }

    var currentLang = getInitialLang();

    // Keep <html lang="..."> in sync from the very first paint.
    if (document.documentElement) {
        document.documentElement.lang = currentLang;
    }

    /**
     * I18n.t(key) — translated string for the current language.
     * Falls back to English, then to the key itself when missing.
     */
    function t(key) {
        if (typeof key !== 'string') {
            return key;
        }
        var dict = I18N_DICT[currentLang];
        if (dict && hasKey(dict, key)) {
            return dict[key];
        }
        var en = I18N_DICT[DEFAULT_LANG];
        if (en && hasKey(en, key)) {
            return en[key];
        }
        return key;
    }

    /**
     * I18n.applyDom() — walk the DOM and translate every element that
     * declares data-i18n / data-i18n-placeholder / data-i18n-aria.
     * Text is written via textContent unless the element also carries the
     * data-i18n-html attribute (opt-in to innerHTML for inline HTML).
     */
    function applyDom() {
        var nodes = document.querySelectorAll('[data-i18n], [data-i18n-placeholder], [data-i18n-aria]');
        var i, el, key;
        for (i = 0; i < nodes.length; i++) {
            el = nodes[i];

            key = el.getAttribute('data-i18n');
            if (key) {
                if (el.hasAttribute('data-i18n-html')) {
                    el.innerHTML = t(key);
                } else {
                    el.textContent = t(key);
                }
            }

            key = el.getAttribute('data-i18n-placeholder');
            if (key) {
                el.setAttribute('placeholder', t(key));
            }

            key = el.getAttribute('data-i18n-aria');
            if (key) {
                el.setAttribute('aria-label', t(key));
            }
        }
    }

    /**
     * I18n.setLang(lang) — switch language, persist the choice, update
     * <html lang>, re-translate the DOM and notify listeners via the
     * 'i18nchanged' custom event (event.detail.lang = new language).
     * Returns the effective language code.
     */
    function setLang(lang) {
        lang = normalizeLang(lang);
        currentLang = lang;

        try {
            localStorage.setItem(STORAGE_KEY, lang);
        } catch (e) {
            // Persisting is best-effort (e.g. private mode may throw).
        }

        if (document.documentElement) {
            document.documentElement.lang = lang;
        }

        applyDom();

        if (document.dispatchEvent) {
            var evt;
            if (typeof CustomEvent === 'function') {
                evt = new CustomEvent('i18nchanged', { detail: { lang: lang } });
            } else {
                evt = document.createEvent('CustomEvent');
                evt.initCustomEvent('i18nchanged', false, false, { lang: lang });
            }
            document.dispatchEvent(evt);
        }

        return lang;
    }

    /** I18n.getLang() — current language ('en' or 'ne'). */
    function getLang() {
        return currentLang;
    }

    /** I18n.toggleLang() — cycle en → ne → en and return the new language. */
    function toggleLang() {
        return setLang(currentLang === 'en' ? 'ne' : 'en');
    }

    return {
        t: t,
        setLang: setLang,
        getLang: getLang,
        applyDom: applyDom,
        toggleLang: toggleLang
    };
})();

window.I18n = I18n;

// Auto-translate declarative UI as soon as the DOM is ready — but only when
// the page actually uses data-i18n attributes (non-i18n pages stay untouched).
if (document.addEventListener) {
    document.addEventListener('DOMContentLoaded', function() {
        if (document.querySelector('[data-i18n], [data-i18n-placeholder], [data-i18n-aria]')) {
            I18n.applyDom();
        }
    });
}
