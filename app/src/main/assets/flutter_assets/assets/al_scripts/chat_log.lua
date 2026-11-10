-- Chat Log for AL Loader.
-- Saves every chat line (server messages, player chat, what you type) to
-- mods/AL Loader/logs/chatlog/<date>.txt and shows them in a searchable window.
--   /chatlog          open or close the viewer
--   /chatlog <text>   open the viewer filtered to <text>
script_name("Chat Log")
script_author("ReSA")
script_version("1.0")
script_description("Saves the chat to a file per day and shows it in a searchable window. /chatlog")

local imgui = require "mimgui"
local ffi = require "ffi"
local sampev = require "samp.events"
local encoding = require "encoding"
encoding.default = "CP1251"
local u8 = encoding.UTF8

local MAX_LINES = 2000 -- kept in memory for the viewer; the file keeps all of them

local show = imgui.new.bool(false)
local search = imgui.new.char[128]()
local follow = imgui.new.bool(true)
local lines = {}
local log_dir = getWorkingDirectory() .. "/logs/chatlog"
local log_file, log_date

-- SA-MP colour tags ({RRGGBB}) are kept in the file but stripped for search.
local function strip_colors(text)
  return (text:gsub("{%x%x%x%x%x%x}", ""))
end

local function open_log()
  local today = os.date("%Y-%m-%d")
  if log_file and log_date == today then
    return log_file
  end
  if log_file then
    log_file:close()
  end
  if not doesDirectoryExist(log_dir) then
    createDirectory(log_dir)
  end
  log_file = io.open(log_dir .. "/" .. today .. ".txt", "a")
  log_date = today
  return log_file
end

local function add(kind, text, color)
  -- Chat text arrives in the server's code page; the viewer and file use UTF-8.
  local line = {
    time = os.date("%H:%M:%S"),
    kind = kind,
    text = u8(text),
    color = color,
  }
  line.plain = strip_colors(line.text):lower()
  lines[#lines + 1] = line
  if #lines > MAX_LINES then
    table.remove(lines, 1)
  end
  local f = open_log()
  if f then
    f:write(string.format("[%s] %s%s\n", line.time, kind == "" and "" or ("<" .. kind .. "> "), strip_colors(line.text)))
    f:flush()
  end
end

function sampev.onServerMessage(color, text)
  add("", text, color)
end

function sampev.onChatMessage(playerId, text)
  local name = sampIsPlayerConnected(playerId) and sampGetPlayerNickname(playerId) or ("#" .. playerId)
  add(name, text, -1)
end

function sampev.onSendChat(message)
  add("me", message, -1)
end

function sampev.onSendCommand(command)
  add("me", command, -1)
end

-- 0xRRGGBBAA as SA-MP sends it, to ImGui's float colour.
local function to_vec4(color)
  if color == nil or color == -1 then
    return imgui.ImVec4(1, 1, 1, 1)
  end
  local r = bit.band(bit.rshift(color, 24), 0xFF) / 255
  local g = bit.band(bit.rshift(color, 16), 0xFF) / 255
  local b = bit.band(bit.rshift(color, 8), 0xFF) / 255
  return imgui.ImVec4(r, g, b, 1)
end

imgui.OnInitialize(function()
  imgui.GetIO().IniFilename = nil
end)

imgui.OnFrame(function() return show[0] end, function()
  local sw, sh = getScreenResolution()
  -- First placement below the chat (the top third), which draws over windows.
  imgui.SetNextWindowPos(imgui.ImVec2(sw * 0.5, sh * 0.34), imgui.Cond.FirstUseEver, imgui.ImVec2(0.5, 0))
  imgui.SetNextWindowSize(imgui.ImVec2(sw * 0.6, sh * 0.58), imgui.Cond.FirstUseEver)
  imgui.Begin("Chat Log", show)

  imgui.PushItemWidth(-160 * AL_DPI_SCALE)
  imgui.InputTextWithHint("##search", "Search...", search, 128)
  imgui.PopItemWidth()
  imgui.SameLine()
  imgui.Checkbox("Follow", follow)
  imgui.SameLine()
  if imgui.Button("Clear") then
    lines = {}
  end

  local filter = ffi.string(search):lower()
  imgui.BeginChild("##lines", imgui.ImVec2(0, 0), true)
  for _, line in ipairs(lines) do
    if filter == "" or line.plain:find(filter, 1, true) then
      -- These ImGui calls are printf-style: chat text goes in as an argument.
      imgui.TextDisabled("%s", line.time)
      imgui.SameLine()
      if line.kind ~= "" then
        imgui.TextColored(imgui.ImVec4(0.55, 0.75, 1, 1), "%s:", line.kind)
        imgui.SameLine()
      end
      imgui.PushStyleColor(imgui.Col.Text, to_vec4(line.color))
      imgui.TextWrapped("%s", strip_colors(line.text))
      imgui.PopStyleColor()
    end
  end
  if follow[0] and imgui.GetScrollY() >= imgui.GetScrollMaxY() - 40 then
    imgui.SetScrollHereY(1.0)
  end
  imgui.EndChild()
  imgui.End()
end)

function main()
  while not isSampAvailable() do
    wait(100)
  end
  sampRegisterChatCommand("chatlog", function(arg)
    if arg ~= nil and arg ~= "" then
      imgui.StrCopy(search, arg)
      show[0] = true
    else
      show[0] = not show[0]
    end
  end)
  wait(-1)
end

function onScriptTerminate(script, quit)
  if script.id == thisScript().id and log_file then
    log_file:close()
    log_file = nil
  end
end
