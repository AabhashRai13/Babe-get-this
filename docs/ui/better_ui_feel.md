# Better UI feel Checklist

Practical techniques to make the app feel more polished than default native apps.

## 1. Animate page and tab transitions

Don't let screens appear instantly when switching tabs or navigating. Use `AnimatedContent` or Navigation Compose transitions to slide pages in from the side.

## 2. Build custom animation sequences for key interactions

Pick the moments in the app that matter (send action, record action, completion) and design a multi-part animation — icon morphs, background expansions, text fades with spring physics. Make it satisfying.

## 3. When using AI to build animations, break it into sub-animations

Don't one-shot "animate this for me." List each piece:
- Rotate the arrow into a checkmark
- Expand the background from the mic icon
- Fade the text in with a spring

Then have the AI execute each one.

## 4. Replace empty states with custom illustrations

Wherever the app shows "no data" or "nothing here," put an illustration instead. Same for loading states or contextual moments.

## 5. Build a base mascot, then generate infinite variations

Get one illustration made (commissioning an artist is cheaper than people think), then feed it into ChatGPT with different prompts to get variations for every empty state, loading screen, and contextual moment.

## 6. Animate illustrations with Rive

Once static illustrations are working, take it further by animating them. Rive supports Android.

## 7. Add haptic feedback to almost every button

Don't be shy with it. Use `HapticFeedbackConstants` and `view.performHapticFeedback()` on taps.

## 8. Vary haptic intensity by context

- Light haptics for high-frequency actions (typing, input fields)
- Heavier haptics for bigger moments (tab switches, confirmations)

Don't make it all the same strength.

## 9. Stop using the default system icons

Default Material icons are fine but generic. Pick a dedicated icon pack — Hero Icons, Phosphor, Lucide, or Tabler.

## 10. Pick one icon style and stay consistent

Thin, thick, filled, or detailed — whichever style is chosen, use it everywhere. Mixing styles is the most common mistake.

## 11. Use a different icon style to indicate state

For the bottom nav, use the thin/outlined version when inactive and the filled/heavier version when active. Don't just change the color — that's basic.