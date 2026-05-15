import React from 'react';
import {
  Chart as ChartJS,
  CategoryScale,
  LinearScale,
  PointElement,
  LineElement,
  Title,
  Tooltip,
  Legend,
} from 'chart.js';
import { Line } from 'react-chartjs-2';

// Register Chart.js components
ChartJS.register(
  CategoryScale,
  LinearScale,
  PointElement,
  LineElement,
  Title,
  Tooltip,
  Legend
);

const ChartComponent = ({ metricsHistory }) => {
  const data = {
    labels: metricsHistory.map((_, index) => `T-${metricsHistory.length - index}`),
    datasets: [
      {
        label: 'Allowed Requests',
        data: metricsHistory.map((m) => m.allowedRequests),
        borderColor: '#22c55e', // green (success-color)
        backgroundColor: 'rgba(34, 197, 94, 0.2)',
        tension: 0.3,
      },
      {
        label: 'Blocked Requests',
        data: metricsHistory.map((m) => m.blockedRequests),
        borderColor: '#ef4444', // red (danger-color)
        backgroundColor: 'rgba(239, 68, 68, 0.2)',
        tension: 0.3,
      },
    ],
  };

  const options = {
    responsive: true,
    maintainAspectRatio: false,
    plugins: {
      legend: {
        position: 'top',
      },
      title: {
        display: true,
        text: 'Allowed vs Blocked Requests',
      },
    },
    scales: {
      y: {
        beginAtZero: true,
        ticks: {
          stepSize: 1,
        }
      },
    },
  };

  return <Line data={data} options={options} />;
};

export default ChartComponent;
