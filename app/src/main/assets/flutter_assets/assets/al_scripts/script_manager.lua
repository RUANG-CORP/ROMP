-- Script Manager for AL Loader.
-- Lists every running script with its author, version and file, reloads or
-- unloads it, starts scripts in the scripts folder that are not running,
-- restarts scripts that stopped on an error, shows the end of AL Loader's
-- log and has a Lua shell that runs code inside the game.
--   /scripts   open or close the manager
--   swipe left on the radar   the same
--   ` (backtick, the key under Esc) on a keyboard   the same
--
-- For script authors: a script that exports these three functions gets an
-- "Enabled" checkbox in the Running tab.
--   local enabled = true
--   EXPORTS = {
--     canToggle = function() return true end,
--     getToggle = function() return enabled end,
--     toggle = function() enabled = not enabled end,
--   }
script_name("Script Manager")
script_author("ReSA")
script_version("1.3")
script_description("Reload, unload, start and restart AL Loader scripts, read the loader log, run Lua in game. /scripts, swipe left on the radar or press ` on a keyboard")

-- The backtick key's Windows virtual-key code (vkeys.VK_OEM_3).
local VK_OEM_3 = 0xC0

local imgui = require "mimgui"
local ffi = require "ffi"
local lfs = require "lfs"
local inspect = require "inspect"
require "widgets"

local show = imgui.new.bool(false)
local tab = 1
local log_lines = {}
local log_checked = 0
local log_total = 0

-- Scripts that stopped while the game ran, newest first, and the ones the
-- player unloaded on purpose (those are not offered as "stopped on an error").
local stopped = {}
local unloaded_by_player = {}

local shell_input = imgui.new.char[4096]()
local shell_lines = {}

local function scripts_dir()
  return getWorkingDirectory() .. "/scripts"
end

local function norm(path)
  return (path:gsub("\\", "/"):lower())
end

-- Scripts in the folder that no running script was loaded from.
local function not_running()
  local running = {}
  for _, s in ipairs(script.list()) do
    running[norm(s.path)] = true
  end
  local out = {}
  for name in lfs.dir(scripts_dir()) do
    if name:match("%.luac?$") then
      local path = scripts_dir() .. "/" .. name
      if not running[norm(path)] then
        out[#out + 1] = { name = name, path = path }
      end
    end
  end
  table.sort(out, function(a, b) return a.name:lower() < b.name:lower() end)
  return out
end

-- Lines of the log hidden by "Clear" (the file itself is never touched: the
-- loader keeps it open and writing).
local log_hidden = 0

local function read_log()
  log_lines = {}
  local f = io.open(getWorkingDirectory() .. "/logs/alloader.log", "r")
  if not f then
    return
  end
  local index = 0
  for line in f:lines() do
    index = index + 1
    if index > log_hidden then
      log_lines[#log_lines + 1] = line
      if #log_lines > 400 then
        table.remove(log_lines, 1)
      end
    end
  end
  f:close()
  log_total = index
end

local function authors_of(s)
  local list = s.authors
  if type(list) == "table" and #list > 0 then
    return table.concat(list, ", ")
  end
  return "-"
end

-- The toggle API of another script, or nil.
local function toggle_api(s)
  local ok, ex = pcall(function() return s.exports end)
  if not ok or type(ex) ~= "table" then
    return nil
  end
  if type(ex.canToggle) ~= "function" or type(ex.getToggle) ~= "function" or type(ex.toggle) ~= "function" then
    return nil
  end
  local can_ok, can = pcall(ex.canToggle)
  if not can_ok or not can then
    return nil
  end
  return ex
end

function onScriptTerminate(s, quit_game)
  if quit_game or s.id == thisScript().id then
    return
  end
  local key = norm(s.path)
  if unloaded_by_player[key] then
    unloaded_by_player[key] = nil
    return
  end
  for i = #stopped, 1, -1 do
    if norm(stopped[i].path) == key then
      table.remove(stopped, i)
    end
  end
  table.insert(stopped, 1, { name = s.name, path = s.path, time = os.date("%H:%M:%S") })
end

-- Lua shell ----------------------------------------------------------------

local function shell_print(text, color)
  shell_lines[#shell_lines + 1] = { text = text, color = color }
  if #shell_lines > 300 then
    table.remove(shell_lines, 1)
  end
end

local function format_value(v)
  if type(v) == "table" then
    return inspect(v, { depth = 2 })
  end
  return tostring(v)
end

local function run_shell(code)
  if code:match("^%s*$") then
    return
  end
  shell_print("> " .. code, imgui.ImVec4(0.55, 0.75, 1, 1))
  -- An expression first, so "getCharHealth(PLAYER_PED)" prints its value.
  local fn, err = loadstring("return " .. code, "shell")
  if not fn then
    fn, err = loadstring(code, "shell")
  end
  if not fn then
    shell_print(tostring(err), imgui.ImVec4(1, 0.45, 0.45, 1))
    return
  end
  -- print() normally goes to the loader log; while shell code runs it also
  -- shows here, as players expect from a shell.
  local log_print, printed = print, false
  print = function(...)
    local parts = {}
    for i = 1, select("#", ...) do
      parts[#parts + 1] = tostring((select(i, ...)))
    end
    shell_print(table.concat(parts, "  "), imgui.ImVec4(1, 1, 1, 1))
    printed = true
    return log_print(...)
  end
  -- Packed with the count, so nil results ("nil") are kept and printed.
  local results = (function(...) return { n = select("#", ...), ... } end)(pcall(fn))
  print = log_print
  if not results[1] then
    shell_print(tostring(results[2]), imgui.ImVec4(1, 0.45, 0.45, 1))
    return
  end
  local n = results.n - 1
  if n == 0 and not printed then
    shell_print("(no value)", imgui.ImVec4(0.6, 0.6, 0.6, 1))
  end
  for i = 2, n + 1 do
    shell_print(format_value(results[i]), imgui.ImVec4(0.85, 0.85, 0.85, 1))
  end
end

-- Window -------------------------------------------------------------------

imgui.OnInitialize(function()
  imgui.GetIO().IniFilename = nil
end)

local TABS = { "Running", "Not running", "Log", "Shell" }

imgui.OnFrame(function() return show[0] end, function()
  local sw, sh = getScreenResolution()
  -- First placement below the chat (the top third), which draws over windows.
  imgui.SetNextWindowPos(imgui.ImVec2(sw * 0.5, sh * 0.34), imgui.Cond.FirstUseEver, imgui.ImVec2(0.5, 0))
  imgui.SetNextWindowSize(imgui.ImVec2(sw * 0.55, sh * 0.58), imgui.Cond.FirstUseEver)
  imgui.Begin("Script Manager", show)

  for i, title in ipairs(TABS) do
    if i > 1 then
      imgui.SameLine()
    end
    local label = title
    if i == 2 and #stopped > 0 then
      label = label .. " (" .. #stopped .. ")"
    end
    if imgui.Button(label .. (tab == i and " *" or "")) then
      tab = i
    end
  end
  imgui.Separator()

  if tab == 4 then
    -- Shell: output above, input line below.
    imgui.BeginChild("##shell_out", imgui.ImVec2(0, -imgui.GetFrameHeightWithSpacing() * 2), true)
    for _, line in ipairs(shell_lines) do
      imgui.PushStyleColor(imgui.Col.Text, line.color)
      imgui.TextWrapped("%s", line.text)
      imgui.PopStyleColor()
    end
    if imgui.GetScrollY() >= imgui.GetScrollMaxY() - 20 then
      imgui.SetScrollHereY(1.0)
    end
    imgui.EndChild()
    imgui.PushItemWidth(-200 * AL_DPI_SCALE)
    local entered = imgui.InputTextWithHint("##shell_in", "Lua code, e.g. getCharHealth(PLAYER_PED)", shell_input, 4096,
      imgui.InputTextFlags.EnterReturnsTrue)
    imgui.PopItemWidth()
    imgui.SameLine()
    if imgui.Button("Run") or entered then
      run_shell(ffi.string(shell_input))
      shell_input[0] = 0
    end
    imgui.SameLine()
    if imgui.Button("Clear") then
      shell_lines = {}
    end
    imgui.End()
    return
  end

  imgui.BeginChild("##body", imgui.ImVec2(0, 0), false)
  if tab == 1 then
    for _, s in ipairs(script.list()) do
      imgui.PushIDInt(s.id)
      imgui.Text("%s", s.name)
      imgui.SameLine()
      imgui.TextDisabled("%s", s.version ~= "" and ("v" .. s.version) or "")
      imgui.TextDisabled("%s  -  %s", authors_of(s), s.filename)
      if s.description ~= "" then
        imgui.TextWrapped("%s", s.description)
      end
      local api = s.id ~= thisScript().id and toggle_api(s) or nil
      if api then
        local ok, state = pcall(api.getToggle)
        local box = imgui.new.bool(ok and state == true)
        if imgui.Checkbox("Enabled", box) then
          pcall(api.toggle)
        end
        imgui.SameLine()
      end
      if imgui.Button("Reload") then
        s:reload()
      end
      if s.id ~= thisScript().id then
        imgui.SameLine()
        if imgui.Button("Unload") then
          unloaded_by_player[norm(s.path)] = true
          s:unload()
        end
      end
      imgui.Separator()
      imgui.PopID()
    end
  elseif tab == 2 then
    if #stopped > 0 then
      imgui.TextColored(imgui.ImVec4(1, 0.7, 0.4, 1), "%s", "Stopped while you played (usually an error - see Log):")
      local restart
      for i, item in ipairs(stopped) do
        imgui.PushIDInt(1000 + i)
        imgui.Text("%s", item.name)
        imgui.SameLine()
        imgui.TextDisabled("%s", item.time)
        imgui.SameLine()
        if imgui.Button("Start again") then
          restart = i
        end
        imgui.PopID()
      end
      if restart then
        script.load(stopped[restart].path)
        table.remove(stopped, restart)
      end
      if imgui.Button("Restart all stopped scripts") then
        for _, item in ipairs(stopped) do
          script.load(item.path)
        end
        stopped = {}
      end
      imgui.Separator()
    end
    local idle = not_running()
    if #idle == 0 then
      imgui.TextDisabled("%s", "Every script in the scripts folder is running.")
    end
    for i, item in ipairs(idle) do
      imgui.PushIDInt(i)
      imgui.Text("%s", item.name)
      imgui.SameLine()
      if imgui.Button("Start") then
        script.load(item.path)
      end
      imgui.PopID()
    end
    imgui.Separator()
    if imgui.Button("Reload all scripts") then
      reloadScripts()
    end
  else
    if localClock() - log_checked > 1 then
      read_log()
      log_checked = localClock()
    end
    for _, line in ipairs(log_lines) do
      local color = line:find("(error)", 1, true) and imgui.ImVec4(1, 0.45, 0.45, 1)
        or line:find("(warning)", 1, true) and imgui.ImVec4(1, 0.8, 0.4, 1)
        or imgui.ImVec4(0.85, 0.85, 0.85, 1)
      imgui.TextColored(color, "%s", line)
    end
    if imgui.Button("Clear") then
      log_hidden = log_total
      log_lines = {}
    end
  end
  imgui.EndChild()
  imgui.End()
end)

function main()
  while not isSampAvailable() do
    wait(100)
  end
  sampRegisterChatCommand("scripts", function()
    show[0] = not show[0]
  end)
  -- Like the mobile loader's Script Manager: a left swipe on the radar opens
  -- or closes the window (the swipe is reported for one frame, at the lift).
  -- On PC there is no radar to swipe: the backtick key does it. A backtick
  -- typed into the shell's input line stays text (the loader keeps a key an
  -- input field took from wasKeyPressed). Loaders older than the key API
  -- have no wasKeyPressed; the swipe and /scripts still work there.
  local has_keys = type(wasKeyPressed) == "function"
  while true do
    wait(0)
    if isWidgetSwipedLeft(WIDGET_RADAR) or (has_keys and wasKeyPressed(VK_OEM_3)) then
      show[0] = not show[0]
    end
  end
end
