import axios from 'axios';

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080/api';

const apiClient = axios.create({
  baseURL: API_BASE_URL,
});

export const sendResourceRequest = async (userId) => {
  try {
    const response = await apiClient.get(`/resource?userId=${userId}`);
    return response.data;
  } catch (error) {
    if (error.response && error.response.status === 429) {
      return error.response.data;
    }
    console.error('Error fetching resource:', error);
    throw error;
  }
};

export const getAdminStats = async (userId) => {
  try {
    const response = await apiClient.get(`/admin/stats?userId=${userId}`);
    return response.data;
  } catch (error) {
    console.error('Error fetching stats:', error);
    throw error;
  }
};

export const getAdminMetrics = async () => {
  try {
    const response = await apiClient.get('/admin/metrics');
    return response.data;
  } catch (error) {
    console.error('Error fetching metrics:', error);
    throw error;
  }
};
