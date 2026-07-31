param(
    [string]$room = $(throw "Usage: .\bot.ps1 -room <ROOMCODE> [-player <name>] [-server <base-url>]"),
    [string]$player = "AI_Bot_$(Get-Random -Maximum 999)",
    # -server e.g. https://xxxx.ngrok-free.app/Multiplayer_Snake_Game (default: localhost:8080)
    [string]$server = "http://localhost:8080/Multiplayer_Snake_Game"
)

function Join-And-Ready {
    $body = "{`"action`":`"join`",`"roomCode`":`"$room`",`"playerName`":`"$player`",`"color`":`"#3b82f6`"}"
    Invoke-WebRequest -Uri "$server/api/room" -Method POST -Body $body -ContentType "application/json" -UseBasicParsing -ErrorAction SilentlyContinue | Out-Null
    $jg = "{`"action`":`"join`",`"roomCode`":`"$room`",`"playerName`":`"$player`"}"
    Invoke-WebRequest -Uri "$server/api/game" -Method POST -Body $jg -ContentType "application/json" -UseBasicParsing -ErrorAction SilentlyContinue | Out-Null
    $r = "{`"action`":`"ready`",`"roomCode`":`"$room`",`"playerName`":`"$player`"}"
    Invoke-WebRequest -Uri "$server/api/game" -Method POST -Body $r -ContentType "application/json" -UseBasicParsing -ErrorAction SilentlyContinue | Out-Null
}

Join-And-Ready
Write-Host "Ready! Chasing food..."

while ($true) {
    Start-Sleep -Milliseconds 300
    try {
        $state = Invoke-WebRequest -Uri "$server/api/game?action=state&room=$room&player=$player" -UseBasicParsing -ErrorAction Stop
        $s = $state.Content | ConvertFrom-Json

        $me = $s.snakes | Where-Object { $_.name -eq $player } | Select-Object -First 1
        if (-not $me) {
            Write-Host "Not in game, rejoining..."
            Join-And-Ready
            continue
        }

        if ($s.gameOver) {
            Write-Host "Game over! Re-readying..."
            Start-Sleep -Seconds 1
            $r = "{`"action`":`"ready`",`"roomCode`":`"$room`",`"playerName`":`"$player`"}"
            Invoke-WebRequest -Uri "$server/api/game" -Method POST -Body $r -ContentType "application/json" -UseBasicParsing -ErrorAction SilentlyContinue | Out-Null
            continue
        }

        if ($s.gameStarted -and $me.alive -and $s.food) {
            $hx = $me.segments[0].x
            $hy = $me.segments[0].y
            $fx = $s.food.x
            $fy = $s.food.y
            $dx = $fx - $hx
            $dy = $fy - $hy
            $cur = $me.direction

            $opp = @{ "UP"="DOWN"; "DOWN"="UP"; "LEFT"="RIGHT"; "RIGHT"="LEFT" }

            if ([Math]::Abs($dx) -gt [Math]::Abs($dy)) {
                $dir = if ($dx -gt 0) { "RIGHT" } else { "LEFT" }
            } else {
                $dir = if ($dy -gt 0) { "DOWN" } else { "UP" }
            }

            if ($opp[$dir] -eq $cur) {
                if ([Math]::Abs($dx) -gt [Math]::Abs($dy)) {
                    $dir = if ($dy -gt 0) { "DOWN" } else { "UP" }
                } else {
                    $dir = if ($dx -gt 0) { "RIGHT" } else { "LEFT" }
                }
            }

            $move = "{`"action`":`"move`",`"roomCode`":`"$room`",`"playerName`":`"$player`",`"direction`":`"$dir`"}"
            Invoke-WebRequest -Uri "$server/api/game" -Method POST -Body $move -ContentType "application/json" -UseBasicParsing -ErrorAction SilentlyContinue | Out-Null
        }
    } catch {
        Write-Host "Error: $_"
        Start-Sleep -Seconds 1
    }
}
