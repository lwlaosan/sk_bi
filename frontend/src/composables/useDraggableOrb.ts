interface Position { x: number; y: number }

/** Viewport-relative position keeps the saved button reachable on smaller screens. */
export function useDraggableOrb(storageKey: string, activate: () => void) {
  const position = ref<Position | null>(null)
  const viewport = ref({ width: window.innerWidth, height: window.innerHeight })
  const dragging = ref(false)
  let gesture: { id: number; startX: number; startY: number; left: number; top: number; original: Position | null } | null = null
  let suppressClick = false
  const bounds = () => {
    const size = viewport.value.width <= 768 ? 52 : 58
    return { x: Math.max(0, viewport.value.width - size - 24), y: Math.max(0, viewport.value.height - size - 24) }
  }
  const clamp = (value: number) => Math.min(1, Math.max(0, value))
  const style = computed(() => {
    if (!position.value) return undefined
    const limit = bounds()
    return { left: `${12 + position.value.x * limit.x}px`, top: `${12 + position.value.y * limit.y}px`, right: 'auto', bottom: 'auto' }
  })
  onMounted(() => {
    try {
      const saved = JSON.parse(localStorage.getItem(storageKey) || 'null')
      if (saved && Number.isFinite(saved.x) && Number.isFinite(saved.y)) position.value = { x: clamp(saved.x), y: clamp(saved.y) }
    } catch { /* Storage may be unavailable; dragging still works for this visit. */ }
  })
  const resize = () => { viewport.value = { width: window.innerWidth, height: window.innerHeight } }
  window.addEventListener('resize', resize)
  onScopeDispose(() => window.removeEventListener('resize', resize))

  function pointerDown(event: PointerEvent) {
    if (!event.isPrimary || event.button !== 0) return
    const target = event.currentTarget as HTMLElement
    const rect = target.getBoundingClientRect()
    suppressClick = false
    gesture = { id: event.pointerId, startX: event.clientX, startY: event.clientY, left: rect.left, top: rect.top, original: position.value }
    target.setPointerCapture(event.pointerId)
  }
  function pointerMove(event: PointerEvent) {
    if (!gesture || gesture.id !== event.pointerId) return
    const dx = event.clientX - gesture.startX, dy = event.clientY - gesture.startY
    if (!dragging.value && Math.hypot(dx, dy) < 6) return
    dragging.value = true
    suppressClick = true
    const limit = bounds()
    position.value = { x: limit.x ? clamp((gesture.left + dx - 12) / limit.x) : 0, y: limit.y ? clamp((gesture.top + dy - 12) / limit.y) : 0 }
    event.preventDefault()
  }
  function pointerUp(event: PointerEvent) {
    if (!gesture || gesture.id !== event.pointerId) return
    if (dragging.value) {
      try { localStorage.setItem(storageKey, JSON.stringify(position.value)) } catch { /* Optional persistence. */ }
    }
    gesture = null
    dragging.value = false
    const target = event.currentTarget as HTMLElement
    if (target.hasPointerCapture(event.pointerId)) target.releasePointerCapture(event.pointerId)
  }
  function pointerCancel() {
    if (gesture) position.value = gesture.original
    gesture = null
    dragging.value = false
  }
  function click(event: MouseEvent) {
    // Keyboard-generated clicks remain usable after a mouse/touch drag.
    if (event.detail !== 0 && suppressClick) { suppressClick = false; event.preventDefault(); return }
    activate()
  }
  return { style, dragging, pointerDown, pointerMove, pointerUp, pointerCancel, click }
}
