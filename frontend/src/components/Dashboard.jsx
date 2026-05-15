import React, { useState, useEffect } from 'react';
import { sendResourceRequest, getAdminStats, getAdminMetrics } from '../services/api';
import ChartComponent from './ChartComponent';

const Dashboard = () => {
  const [userId, setUserId] = useState('');
  
  // Resource Request State
  const [requestStatus, setRequestStatus] = useState(null);
  
  // Stats State
  const [stats, setStats] = useState(null);
  
  // Metrics State
  const [metrics, setMetrics] = useState({ allowedRequests: 0, blockedRequests: 0 });
  const [metricsHistory, setMetricsHistory] = useState([]);

  // Auto-fetch metrics every 3 seconds
  useEffect(() => {
    const fetchMetrics = async () => {
      try {
        const data = await getAdminMetrics();
        setMetrics(data);
        setMetricsHistory(prev => {
          // Add timestamp for chart updates
          const newHistory = [...prev, { ...data, timestamp: Date.now() }];
          // Keep only the last 20 data points to avoid chart clutter
          return newHistory.slice(-20);
        });
      } catch (error) {
        console.error("Failed to fetch metrics", error);
      }
    };

    fetchMetrics(); // initial fetch
    const intervalId = setInterval(fetchMetrics, 3000);

    return () => clearInterval(intervalId);
  }, []);

  const handleSendRequest = async () => {
    if (!userId) return alert('Please enter a User ID');
    try {
      const data = await sendResourceRequest(userId);
      setRequestStatus(data);
    } catch (error) {
      console.error(error);
      if (error.response && error.response.status === 503) {
        alert('Backend Error (503): Redis server is down or not installed! Please make sure Redis is running on localhost:6379.');
      } else {
        alert('Error sending request. Is the backend running?');
      }
    }
  };

  const handleGetStats = async () => {
    if (!userId) return alert('Please enter a User ID');
    try {
      const data = await getAdminStats(userId);
      setStats(data);
    } catch (error) {
      console.error(error);
      if (error.response && error.response.status === 503) {
        alert('Backend Error (503): Redis server is down or not installed! Please make sure Redis is running on localhost:6379.');
      } else {
        alert('Error getting stats. Is the backend running?');
      }
    }
  };

  return (
    <div className="dashboard-container">
      <header className="dashboard-header">
        <h1>Rate Limiter Dashboard</h1>
        <p>Monitor your distributed rate limiting system in real-time</p>
      </header>

      <main className="dashboard-main">
        {/* Left Column: Actions & Results */}
        <div className="column">
          
          {/* Action Card */}
          <section className="card action-card">
            <h2>User Operations</h2>
            <div className="input-group">
              <label htmlFor="userId">User ID:</label>
              <input 
                id="userId"
                type="text" 
                value={userId} 
                onChange={(e) => setUserId(e.target.value)}
                placeholder="Enter User ID (e.g. 123)"
              />
            </div>
            <div className="button-group">
              <button onClick={handleSendRequest} className="btn primary">Send Request</button>
              <button onClick={handleGetStats} className="btn secondary">Get Stats</button>
            </div>
          </section>

          {/* Request Status Result */}
          {requestStatus && (
            <section className={`card status-card ${requestStatus.allowed ? 'allowed' : 'blocked'}`}>
              <h2>Request Status</h2>
              <div className="status-indicator">
                {requestStatus.allowed ? '✅ Allowed' : '❌ Blocked'}
              </div>
              <div className="details">
                {requestStatus.message && <p><strong>Message:</strong> {requestStatus.message}</p>}
                <p><strong>Remaining Tokens:</strong> {requestStatus.remainingTokens}</p>
                {!requestStatus.allowed && requestStatus.retryAfterSeconds !== undefined && (
                  <p><strong>Retry After:</strong> {requestStatus.retryAfterSeconds}s</p>
                )}
              </div>
            </section>
          )}

          {/* User Stats Result */}
          {stats && (
            <section className="card stats-card">
              <h2>User Stats</h2>
              <div className="details">
                <p><strong>Remaining Tokens:</strong> {stats.remainingTokens}</p>
                <p><strong>Max Tokens:</strong> {stats.maxTokens}</p>
                <p><strong>Refill Rate:</strong> {stats.refillRate} tokens/sec</p>
                {stats.lastRefillTime && (
                  <p><strong>Last Refill:</strong> {new Date(stats.lastRefillTime).toLocaleString()}</p>
                )}
              </div>
            </section>
          )}

        </div>

        {/* Right Column: Global Metrics & Chart */}
        <div className="column">
          
          {/* Global Metrics */}
          <section className="card metrics-card">
            <h2>Global Metrics</h2>
            <div className="metrics-grid">
              <div className="metric-box allowed-metric">
                <h3>Allowed</h3>
                <div className="metric-value">{metrics.allowedRequests}</div>
              </div>
              <div className="metric-box blocked-metric">
                <h3>Blocked</h3>
                <div className="metric-value">{metrics.blockedRequests}</div>
              </div>
            </div>
          </section>

          {/* Chart Section */}
          <section className="card chart-card">
            <h2>Traffic History</h2>
            <div className="chart-container" style={{ position: 'relative', height: '300px', width: '100%' }}>
              {metricsHistory.length > 0 ? (
                <ChartComponent metricsHistory={metricsHistory} />
              ) : (
                <div className="empty-chart">Waiting for data...</div>
              )}
            </div>
          </section>

        </div>
      </main>
    </div>
  );
};

export default Dashboard;
