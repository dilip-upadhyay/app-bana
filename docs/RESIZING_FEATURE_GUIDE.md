# 📏 Component Resizing Feature - User Guide

**Date:** October 29, 2025  
**Status:** ✅ Implemented

## Overview

You can now **increase and decrease the width and height** of any component on the canvas, including grid cells, containers, inputs, buttons, and more. The Properties Inspector provides intuitive controls for precise sizing.

---

## 🌟 Key Features

### 1. **Width & Height Controls**
- Set exact dimensions with CSS units
- Supports: `px`, `%`, `rem`, `em`, `vh`, `vw`, `auto`
- Live preview as you type

### 2. **Min/Max Constraints**
- Set minimum width/height to prevent components from getting too small
- Set maximum width/height to constrain growth
- Perfect for responsive layouts

### 3. **Quick Size Buttons**
- **Full Width** - Sets width to 100%
- **Half Width** - Sets width to 50%
- **Auto** - Automatic sizing based on content
- **Preset Sizes** - 200×200, 300×200, 400×300

### 4. **Grid Cell Resizing**
- Resize individual grid cells
- Set min-height to ensure consistent row heights
- Width controlled by grid columns (but can override)

---

## 📖 How to Use

### Resizing Components

1. **Select a Component**
   - Click any component on the canvas
   - Properties Inspector appears on the right

2. **Edit Dimensions**
   - Find the **📏 Dimensions** section
   - Enter values in Width/Height fields
   - Press Enter or Tab to apply

3. **Use Quick Sizes**
   - Click preset buttons for common sizes
   - Instantly resize components

### Example Values

| Input | Result |
|-------|--------|
| `200px` | Fixed 200 pixels |
| `50%` | 50% of parent width |
| `auto` | Automatic based on content |
| `100%` | Full width of parent |
| `20rem` | 20 × root font size |
| `300px` | Fixed 300 pixels |

---

## 🎯 Common Use Cases

### 1. **Resize Input Fields**
```
Width: 300px
Height: 40px
```
Perfect for form inputs with consistent sizing.

### 2. **Full-Width Button**
```
Width: 100%
Height: 48px
```
Button spans entire container width.

### 3. **Fixed Card Size**
```
Width: 350px
Height: 400px
```
Create consistent card layouts.

### 4. **Responsive Grid Cell**
```
Min-Width: 200px
Min-Height: 150px
Width: auto
Height: auto
```
Cell grows/shrinks but maintains minimums.

### 5. **Constrained Container**
```
Max-Width: 600px
Width: 100%
```
Container grows up to 600px then stops.

---

## 📐 Grid Cell Resizing

### Grid Cells Special Behavior

Grid cells are part of the grid layout, so:
- **Width** is usually controlled by `grid-template-columns`
- **Height** can be set individually per cell
- **Min-Height** ensures cells don't collapse

### Recommended Approach

1. **Set Min-Height on Cells**
   ```
   Min-Height: 150px
   ```
   Ensures all cells have minimum height.

2. **Override Width for Specific Cells**
   ```
   Width: 100%
   ```
   Cell spans its grid column.

3. **Use Padding for Spacing**
   ```
   (Add padding controls coming soon)
   ```

---

## 💡 Pro Tips

### ✅ Best Practices

1. **Use Relative Units for Responsive**
   - Use `%` or `rem` instead of `px`
   - Adapts to different screen sizes

2. **Set Min-Width on Inputs**
   - Prevents inputs from collapsing
   - `min-width: 200px` is good default

3. **Use Max-Width for Readability**
   - Long text containers: `max-width: 600px`
   - Improves reading experience

4. **Grid Cells: Min-Height**
   - Set `min-height: 100px` or more
   - Prevents empty cells from disappearing

5. **Buttons: Fixed Height**
   - `height: 40px` or `height: 48px`
   - Consistent button sizing

### ❌ Common Mistakes

1. **Don't mix units inconsistently**
   - Bad: `width: 50%; min-width: 200rem;`
   - Good: `width: 50%; min-width: 200px;`

2. **Don't set height on text elements**
   - Let text determine height naturally
   - Use `min-height` if needed

3. **Don't forget units**
   - Bad: `width: 200`
   - Good: `width: 200px`

---

## 🎨 Visual Examples

### Small Button (Compact)
```
Width: 120px
Height: 32px
```

### Medium Button (Default)
```
Width: 180px
Height: 40px
```

### Large Button (Primary Action)
```
Width: 240px
Height: 48px
```

### Input Field (Short)
```
Width: 200px
Height: 36px
```

### Input Field (Medium)
```
Width: 300px
Height: 40px
```

### Input Field (Full Width)
```
Width: 100%
Height: 40px
```

### Grid Cell (Compact)
```
Min-Width: 150px
Min-Height: 100px
```

### Grid Cell (Standard)
```
Min-Width: 200px
Min-Height: 150px
```

### Grid Cell (Large)
```
Min-Width: 300px
Min-Height: 200px
```

---

## 🔧 Technical Details

### How It Works

1. **Properties Inspector** reads current styles
2. **Extracts dimension values** using regex
3. **User edits dimensions** in input fields
4. **Updates component style** property
5. **TreeStore notifies listeners** of change
6. **Canvas re-renders** with new dimensions

### CSS Generation

Input:
```
Width: 300px
Height: 200px
```

Generated CSS:
```css
style="width: 300px; height: 200px;"
```

### Style Property Management

- **Preserves other styles** - Only updates dimensions
- **Merges with existing** - Doesn't overwrite other properties
- **Auto-formats** - Adds semicolons and spacing

---

## 🐛 Troubleshooting

### Component not resizing?

**Check:**
1. Is the component selected? (purple border)
2. Did you press Enter after typing?
3. Are units included? (px, %, etc.)
4. Is parent container constraining size?

### Grid cell not changing width?

**Solution:**
- Grid columns control width by default
- Set explicit width to override: `width: 100%`
- Or modify grid's `grid-template-columns`

### Size jumping back?

**Possible causes:**
1. CSS class override - Check className
2. Parent container constraints
3. Invalid CSS value

---

## 🚀 Coming Soon

Future enhancements planned:
- [ ] Padding & Margin controls
- [ ] Visual resize handles (drag to resize)
- [ ] Lock aspect ratio option
- [ ] Responsive breakpoint controls
- [ ] Copy dimensions between components
- [ ] Dimension history/presets

---

## 📝 Quick Reference

### Common Dimensions

| Component | Typical Size |
|-----------|-------------|
| Small Button | 120px × 32px |
| Medium Button | 180px × 40px |
| Large Button | 240px × 48px |
| Text Input | 300px × 40px |
| Textarea | 100% × 120px |
| Grid Cell | 200px × 150px (min) |
| Card | 350px × 400px |
| Container | 100% × auto |

### CSS Units

| Unit | Description | Example |
|------|-------------|---------|
| `px` | Pixels (fixed) | 200px |
| `%` | Percentage of parent | 50% |
| `rem` | Relative to root font | 2rem |
| `em` | Relative to font size | 1.5em |
| `vh` | Viewport height % | 50vh |
| `vw` | Viewport width % | 80vw |
| `auto` | Automatic | auto |

---

**Happy Resizing!** 🎉

Your components and grid cells can now be precisely sized for perfect layouts!

