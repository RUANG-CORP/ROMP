-- Copyright (c) AL Loader (Alyn Lua Loader), 2026
-- Built-in library: sa_renderfix
-- Lua package is distributed under the MIT license

--[[
  Makes the script-drawing opcodes place things where a PC script expects.

  Script drawing works in a 640x448 space. The PC game stretches that space
  over the whole screen: screen x = x * W / 640. ReSA, like the SA mobile
  game it follows, keeps it 4:3 and centres it instead:

    DRAW_RECT / DRAW_SPRITE / DRAW_SPRITE_WITH_ROTATION
      (Script8.cpp, SACorrectScriptRectangle - mobile's CorrectAspect)
      screen x = (W - H * 4/3) / 2 + x * H / 480,  width = w * H / 480
    DISPLAY_TEXT and its number/float variants, SET_TEXT_WRAPX
      (Hud.cpp DrawScriptPhase, SCALE_AND_CENTER_X)
      the same mapping;  SET_TEXT_CENTRE_SIZE (SCREEN_SCALE_X) a width
    y and heights: y * H / 448 in both games.

  Requiring this module wraps those functions so a script written for the
  stretched PC space lands on the same screen spot here:

    x' = 320 + (x - 320) * k,   w' = w * k,   k = (W / H) / (4 / 3)

  k is computed per call, so a resized PC window keeps working.

  Left alone on purpose:
    setTextScale     ReSA scales glyphs by the 4:3 height (SCREEN_SCALE_X),
                     so text keeps its proportions; only its position moves.
    getStringWidth   CFont::GetStringWidth in screen pixels, as on PC.
    render*          renderDrawBox and friends already take screen pixels.

  displayTextClamped / displayTextWithNumberClamped are SA mobile opcodes
  (0A70/0A71) the engine does not handle yet; they are wrapped so their
  position and width follow the same rule once it does.
]]

if AL_VERSION == nil or _G.__al_sa_renderfix then
  return nil
end
_G.__al_sa_renderfix = true

local function factor()
  local w, h = getScreenResolution()
  if not w or not h or w <= 0 or h <= 0 then
    return 1
  end
  return (w / h) * 0.75
end

local function x_of(x)
  return 320 + (x - 320) * factor()
end

local function width_of(w)
  return w * factor()
end

local function wrap(name, make)
  local original = _G[name]
  if type(original) == 'function' then
    _G[name] = make(original)
  end
end

-- (x, y) is the centre and (w, h) the size, in all three.
wrap('drawRect', function(f)
  return function(x, y, w, h, ...)
    return f(x_of(x), y, width_of(w), h, ...)
  end
end)
wrap('drawSprite', function(f)
  return function(texture, x, y, w, h, ...)
    return f(texture, x_of(x), y, width_of(w), h, ...)
  end
end)
wrap('drawSpriteWithRotation', function(f)
  return function(texture, x, y, w, h, ...)
    return f(texture, x_of(x), y, width_of(w), h, ...)
  end
end)

for _, name in ipairs({ 'displayText', 'displayTextWithNumber', 'displayTextWith2Numbers', 'displayTextWithFloat' }) do
  wrap(name, function(f)
    return function(x, ...)
      return f(x_of(x), ...)
    end
  end)
end

wrap('displayTextClamped', function(f)
  return function(x, y, key, width)
    return f(x_of(x), y, key, width_of(width))
  end
end)
wrap('displayTextWithNumberClamped', function(f)
  return function(x, y, key, number, width)
    return f(x_of(x), y, key, number, width_of(width))
  end
end)

wrap('setTextWrapx', function(f)
  return function(x)
    return f(x_of(x))
  end
end)
wrap('setTextCentreSize', function(f)
  return function(w)
    return f(width_of(w))
  end
end)

return nil
