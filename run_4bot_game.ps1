$base = "http://localhost:8080/Multiplayer_Snake_Game"
$players = @(
    @{ name="AI_Red"; color="#e94560" },
    @{ name="AI_Green"; color="#16a34a" },
    @{ name="AI_Blue"; color="#3b82f6" },
    @{ name="AI_Yellow"; color="#facc15" }
)

# 1. Create room
$r = Invoke-WebRequest -Uri "$base/api/room" -Method POST -Body '{"action":"create","playerName":"AI_Red","color":"#e94560"}' -ContentType "application/json" -UseBasicParsing -TimeoutSec 5
$room = ($r.Content | ConvertFrom-Json).roomCode
Write-Host "CREATED ROOM: $room"

# 2. Join all 4 players
foreach ($p in $players) {
    $r1 = Invoke-WebRequest -Uri "$base/api/room" -Method POST -Body ('{"action":"join","roomCode":"' + $room + '","playerName":"' + $p.name + '","color":"' + $p.color + '"}') -ContentType "application/json" -UseBasicParsing -TimeoutSec 5
    $r2 = Invoke-WebRequest -Uri "$base/api/game" -Method POST -Body ('{"action":"join","roomCode":"' + $room + '","playerName":"' + $p.name + '"}') -ContentType "application/json" -UseBasicParsing -TimeoutSec 5
    Write-Host ("{0}: joinRoom={1} joinGame={2}" -f $p.name, $r1.Content, $r2.Content)
}

# 3. Ready ALL at once (triggers round start)
foreach ($p in $players) {
    $r = Invoke-WebRequest -Uri "$base/api/game" -Method POST -Body ('{"action":"ready","roomCode":"' + $room + '","playerName":"' + $p.name + '"}') -ContentType "application/json" -UseBasicParsing -TimeoutSec 5
    Write-Host ("{0} READY: {1}" -f $p.name, $r.Content)
}

Write-Host "ALL READY - ROUND STARTED"

# 4. Launch simple movement bots (send move every 300ms)
$scriptBlock = {
    param($room, $player, $base)
    $dirs = @("UP","RIGHT","DOWN","LEFT")
    $di = 0
    while ($true) {
        Start-Sleep -Milliseconds 300
        try {
            $state = Invoke-WebRequest -Uri "$base/api/game?action=state&room=$room&player=$player" -UseBasicParsing -TimeoutSec 3
            $s = $state.Content | ConvertFrom-Json
            if ($s.gameOver) { break }
            $me = $s.snakes | Where-Object { $_.name -eq $player } | Select-Object -First 1
            if (-not $me -or -not $me.alive) { continue }
            $moveDir = $dirs[($global:di++) % 4]
            $mv = '{"action":"move","roomCode":"' + $room + '","playerName":"' + $player + '","direction":"' + $moveDir + '"}'
            Invoke-WebRequest -Uri "$base/api/game" -Method POST -Body $mv -ContentType "application/json" -UseBasicParsing -TimeoutSec 3 | Out-Null
        } catch { }
    }
}

foreach ($p in $players) {
    Start-Job -ScriptBlock $scriptBlock -ArgumentList $room, $p.name, "http://localhost:8080/Multiplayer_Snake_Game" | Out-Null
    Write-Host "Launched movement bot for $p.name"
}

Write-Host "GAME RUNNING with 4 AI players in room $room"
Write-Host "Polling state..."

# Monitor
for ($i = 0; $i -lt 60; $i++) {
    Start-Sleep -Seconds 1
    $s = (Invoke-WebRequest -Uri "http://localhost:8080/Multiplayer_Snake_Game/api/game?action=state&room=$room&player=x" -UseBasicParsing -TimeoutSec 5).Content | ConvertFrom-Json
    $alive = $s.snakes | Where-Object { $_.alive } | Measure-Object | Select-Object -ExpandProperty Count
    Write-Host ("tick={0} go={1} started={2} alive={3}/{4}" -f $s.tick, $s.gameOver, $s.gameStarted, $alive, $s.snakes.Count)
    if ($s.gameOver) { break }
}