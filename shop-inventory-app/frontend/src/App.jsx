import { useState, useEffect, useRef } from 'react'
import './App.css'

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080'

export default function App() {
  const [inventory, setInventory] = useState([])
  const [orders, setOrders] = useState([])
  const [notifications, setNotifications] = useState([])

  // Notification Bell Popover State
  const [showNotifs, setShowNotifs] = useState(false)
  const notifRef = useRef(null)

  // Cart & Order Form State
  const [cart, setCart] = useState([])
  const [selectedProductId, setSelectedProductId] = useState('')
  const [selectedQuantity, setSelectedQuantity] = useState(1)
  const [loading, setLoading] = useState(false)
  const [orderFeedback, setOrderFeedback] = useState(null)

  useEffect(() => {
    refreshAll()
  }, [])

  // Close popover when clicking anywhere outside
  useEffect(() => {
  function handleClickOutside(e) {
    if (notifRef.current && !notifRef.current.contains(e.target)) {
      setShowNotifs(false)
    }
  }
  document.addEventListener('mousedown', handleClickOutside)
  return () => document.removeEventListener('mousedown', handleClickOutside)
}, [])

  useEffect(() => {
    if (!selectedProductId && inventory.length > 0) {
      setSelectedProductId(inventory[0].productId)
    }
  }, [inventory, selectedProductId])

  async function refreshAll() {
    await Promise.all([fetchInventory(), fetchOrders(), fetchNotifications()])
  }

  async function fetchInventory() {
    try {
      const res = await fetch(`${API_BASE_URL}/api/inventory`)
      if (!res.ok) return setInventory([])
      const data = await res.json()
      setInventory(Array.isArray(data) ? data : [])
    } catch {
      setInventory([])
    }
  }

  async function fetchOrders() {
    try {
      const res = await fetch(`${API_BASE_URL}/api/orders`)
      if (!res.ok) return setOrders([])
      const data = await res.json()
      setOrders(Array.isArray(data) ? data : [])
    } catch {
      setOrders([])
    }
  }

  async function fetchNotifications() {
    try {
      const res = await fetch(`${API_BASE_URL}/api/notifications`)
      if (!res.ok) return setNotifications([])
      const data = await res.json()
      setNotifications(Array.isArray(data) ? data : [])
    } catch {
      setNotifications([])
    }
  }

  function handleAddToCart(e) {
    e.preventDefault()
    if (!selectedProductId || selectedQuantity <= 0) return

    const product = inventory.find(p => p.productId === selectedProductId)
    const existingIndex = cart.findIndex(item => item.productId === selectedProductId)

    if (existingIndex > -1) {
      const updatedCart = [...cart]
      updatedCart[existingIndex].quantity += Number(selectedQuantity)
      setCart(updatedCart)
    } else {
      setCart([
        ...cart,
        {
          productId: selectedProductId,
          name: product ? product.name : selectedProductId,
          quantity: Number(selectedQuantity)
        }
      ])
    }
  }

  function handleRemoveFromCart(productId) {
    setCart(cart.filter(item => item.productId !== productId))
  }

  async function handlePlaceOrder() {
    if (cart.length === 0) return
    setLoading(true)
    setOrderFeedback(null)

    const payload = {
      items: cart.map(item => ({ productId: item.productId, quantity: item.quantity }))
    }

    try {
      const res = await fetch(`${API_BASE_URL}/api/orders`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload)
      })
      const result = await res.json()
      setOrderFeedback(result)
      if (result.status === 'CONFIRMED') {
        setCart([])
      }
      await refreshAll()
    } catch {
      setOrderFeedback({
        status: 'ERROR',
        reason: 'Network gateway timeout: Failed to transmit order request.'
      })
    } finally {
      setLoading(false)
    }
  }

  async function handleCancelOrder(orderId) {
    try {
      const res = await fetch(`${API_BASE_URL}/api/orders/${orderId}/cancel`, {
        method: 'POST'
      })
      if (res.ok) {
        await refreshAll()
      }
    } catch (err) {
      console.error(err)
    }
  }

  const latestNotification = notifications.length > 0 ? notifications[notifications.length - 1] : null

  return (
    <div className="layout-wrapper">
      {/* Formal Top Navigation Header with Notification Bell */}
      <header className="site-header">
        <div className="header-inner">
          <div className="brand-group">
            <span className="brand-badge">CIT-U IT</span>
            <div className="brand-text">
              <h1 className="brand-title">Shop & Inventory Control Portal</h1>
              <span className="brand-meta">Lab 2: Modular Monolith Architecture</span>
            </div>
          </div>

          <div className="header-actions">
            <span className="node-indicator">
              <span className="status-dot"></span> Core System Active
            </span>

            {/* Notification Bell Dropdown Trigger */}
            <div className="notif-wrapper" ref={notifRef}>
  <button 
    type="button"
    className={`btn-bell ${showNotifs ? 'active' : ''}`}
    onClick={() => setShowNotifs(!showNotifs)}
  >
    🔔
    {notifications.length > 0 && (
      <span className="bell-badge">{notifications.length}</span>
    )}
  </button>

  {showNotifs && (
    <div className="notif-popover">
      <div className="popover-header">
        <span className="popover-title">Notification History</span>
        <span className="popover-count">{notifications.length} events</span>
      </div>

      <div className="popover-list">
        {notifications.length === 0 ? (
          <div className="popover-empty">No logged events yet.</div>
        ) : (
          notifications.slice().reverse().map((n, i) => {
            const isReorder = (n.message || '').includes('Reorder needed')
            const isRejected = (n.message || '').includes('rejected')
            return (
              <div 
                key={n.id || i} 
                className={`popover-item ${isReorder ? 'border-amber' : isRejected ? 'border-red' : 'border-green'}`}
              >
                <p className="popover-msg">{n.message}</p>
                {n.createdAt && (
                  <span className="popover-time">
                    {new Date(n.createdAt).toLocaleTimeString()}
                  </span>
                )}
              </div>
            )
          })
        )}
      </div>
    </div>
  )}
</div>

            <button className="btn-secondary" onClick={refreshAll}>
              Synchronize Data
            </button>
          </div>
        </div>
      </header>

      {/* Top Notification Announcement Bar */}
      <div className="top-banner-bar">
        <div className="top-banner-content">
          <span className="banner-tag">LATEST SYSTEM EVENT</span>
          <span className="banner-message">
            {latestNotification 
              ? latestNotification.message 
              : 'All modules operational. Awaiting incoming domain event transmissions...'}
          </span>
          {latestNotification?.createdAt && (
            <span className="banner-time">
              {new Date(latestNotification.createdAt).toLocaleTimeString()}
            </span>
          )}
        </div>
      </div>

      {/* Main Body */}
      <main className="main-content">
        <div className="dashboard-grid">
          
          {/* Left Column */}
          <div className="dashboard-column">
            {/* Order Configuration Desk */}
            <section className="dashboard-card">
              <div className="card-heading-group">
                <h2 className="card-title">Order Processing Desk</h2>
                <span className="card-subtitle">Multi-Item Transactional Dispatch</span>
              </div>

              <form onSubmit={handleAddToCart} className="order-form-row">
                <div className="form-group flex-2">
                  <label>SKU Catalog</label>
                  <select
                    className="form-control"
                    value={selectedProductId}
                    onChange={e => setSelectedProductId(e.target.value)}
                  >
                    {inventory.map(prod => (
                      <option key={prod.productId} value={prod.productId}>
                        {prod.productId} — {prod.name} ({prod.stock} in stock)
                      </option>
                    ))}
                  </select>
                </div>
                <div className="form-group flex-1">
                  <label>Quantity</label>
                  <input
                    type="number"
                    min="1"
                    className="form-control"
                    value={selectedQuantity}
                    onChange={e => setSelectedQuantity(Math.max(1, parseInt(e.target.value) || 1))}
                  />
                </div>
                <button type="submit" className="btn-accent">
                  Add to Cart
                </button>
              </form>

              {/* Staged Cart Table */}
              <div className="cart-container">
                <h3 className="section-label">Staged Order Items ({cart.length})</h3>
                {cart.length === 0 ? (
                  <div className="empty-slate">No items currently staged in order manifest.</div>
                ) : (
                  <div className="staged-list">
                    {cart.map(item => (
                      <div key={item.productId} className="staged-item-row">
                        <div className="staged-item-info">
                          <span className="staged-qty">{item.quantity}×</span>
                          <span className="staged-name">{item.name}</span>
                          <code className="sku-tag">{item.productId}</code>
                        </div>
                        <button
                          type="button"
                          className="btn-danger-text"
                          onClick={() => handleRemoveFromCart(item.productId)}
                        >
                          Remove
                        </button>
                      </div>
                    ))}
                    <button
                      type="button"
                      className="btn-primary-block"
                      onClick={handlePlaceOrder}
                      disabled={loading}
                    >
                      {loading ? 'Committing Transaction...' : 'Place Multi-Item Order'}
                    </button>
                  </div>
                )}
              </div>

              {/* Order Submission Response */}
              {orderFeedback && (
                <div className={`status-callout ${orderFeedback.status === 'CONFIRMED' ? 'callout-success' : 'callout-danger'}`}>
                  <div className="callout-header">
                    <strong>Transaction Result: {orderFeedback.status}</strong>
                  </div>
                  {orderFeedback.reason && <p className="callout-detail">{orderFeedback.reason}</p>}
                </div>
              )}
            </section>

            {/* Inventory Table */}
            <section className="dashboard-card">
              <div className="card-heading-group">
                <h2 className="card-title">Live Inventory Status</h2>
                <span className="card-subtitle">Real-Time Stock Threshold Monitor</span>
              </div>

              <div className="table-wrapper">
                <table className="formal-table">
                  <thead>
                    <tr>
                      <th>Product SKU</th>
                      <th>Product Description</th>
                      <th style={{ textAlign: 'right' }}>Stock Level</th>
                      <th style={{ textAlign: 'right' }}>Status</th>
                    </tr>
                  </thead>
                  <tbody>
                    {inventory.map(prod => {
                      const isOutOfStock = prod.stock === 0
                      const isLowStock = prod.stock > 0 && prod.stock <= 5
                      return (
                        <tr
                          key={prod.productId}
                          className={isOutOfStock ? 'row-depleted' : isLowStock ? 'row-low-stock' : ''}
                        >
                          <td><code className="sku-tag">{prod.productId}</code></td>
                          <td className="item-name-cell">{prod.name}</td>
                          <td className="stock-cell">{prod.stock}</td>
                          <td style={{ textAlign: 'right' }}>
                            {isOutOfStock ? (
                              <span className="pill pill-danger">Out of Stock</span>
                            ) : isLowStock ? (
                              <span className="pill pill-warning">Low Stock (≤ 5)</span>
                            ) : (
                              <span className="pill pill-success">Normal</span>
                            )}
                          </td>
                        </tr>
                      )
                    })}
                  </tbody>
                </table>
              </div>
            </section>
          </div>

          {/* Right Column */}
          <div className="dashboard-column">
            {/* Order History Ledger */}
            <section className="dashboard-card">
              <div className="card-heading-group">
                <h2 className="card-title">Order Transaction Ledger</h2>
                <span className="card-subtitle">Historical Orders & Cancellation Desk</span>
              </div>

              {orders.length === 0 ? (
                <div className="empty-slate">No transactional records registered yet.</div>
              ) : (
                <div className="ledger-stack">
                  {orders.map(order => (
                    <div key={order.orderId || order.id} className="ledger-record">
                      <div className="record-header">
                        <div className="record-title-group">
                          <span className="record-id">Order #{order.orderId || order.id}</span>
                          <span className={`pill ${
                            order.status === 'CONFIRMED' ? 'pill-success' :
                            order.status === 'CANCELLED' ? 'pill-neutral' : 'pill-danger'
                          }`}>
                            {order.status}
                          </span>
                        </div>
                        {order.status === 'CONFIRMED' && (
                          <button
                            className="btn-cancel"
                            onClick={() => handleCancelOrder(order.orderId || order.id)}
                          >
                            Cancel & Restock
                          </button>
                        )}
                      </div>

                      {order.reason && <p className="record-rejection">{order.reason}</p>}

                      <ul className="record-items">
                        {(order.items || []).map((item, idx) => (
                          <li key={idx}>
                            <strong>{item.quantity}×</strong> {item.name || item.productId}
                          </li>
                        ))}
                      </ul>
                    </div>
                  ))}
                </div>
              )}
            </section>

            {/* In-Monolith Domain Event Feed */}
            <section className="dashboard-card">
              <div className="card-heading-group">
                <h2 className="card-title">Domain Event Stream</h2>
                <span className="card-subtitle">Internal Publish / Subscribe Notification Log</span>
              </div>

              {notifications.length === 0 ? (
                <div className="empty-slate">Domain event queue is currently clear.</div>
              ) : (
                <div className="event-stream">
                  {notifications.slice().reverse().map((notif, idx) => {
                    const isReorder = (notif.message || '').includes('Reorder needed')
                    const isRejected = (notif.message || '').includes('rejected')
                    return (
                      <div
                        key={notif.id || idx}
                        className={`event-card ${isReorder ? 'event-amber' : isRejected ? 'event-red' : 'event-green'}`}
                      >
                        <div className="event-body">
                          <p className="event-text">{notif.message}</p>
                          {notif.createdAt && (
                            <span className="event-timestamp">
                              {new Date(notif.createdAt).toLocaleTimeString()}
                            </span>
                          )}
                        </div>
                      </div>
                    )
                  })}
                </div>
              )}
            </section>
          </div>

        </div>
      </main>

      {/* Institutional Footer */}
      <footer className="site-footer">
        <div className="footer-inner">
          <div className="footer-col">
            <span className="footer-brand">Modular Monolith Management Interface</span>
            <p className="footer-note">Cebu Institute of Technology – University • College of Computer Studies</p>
          </div>
          <div className="footer-col footer-col-right">
            <span>Integration Protocol: Spring EventListener & In-Process Monolith</span>
            <span>REST Gateway: <code className="sku-tag">http://localhost:8080/api</code></span>
          </div>
        </div>
      </footer>
    </div>
  )
}