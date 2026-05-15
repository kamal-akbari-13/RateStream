# Deploying RateStream to Render ☁️

Render is a fantastic, free-tier friendly platform to host your entire stack. We need to deploy three components: **Redis**, the **Spring Boot Backend**, and the **React Frontend**.

Follow these steps exactly to get your full project live on the internet!

---

## Step 1: Prepare Your Code for Production

Before uploading to Render, we need to ensure your code can dynamically accept production URLs using Environment Variables.

### 1.1 Update Backend for Redis
By default, the backend connects to `localhost:6379`. We need it to connect to Render's Redis URL.
Open your backend configuration (e.g., `Backend/src/main/resources/application.properties` or `application.yml`) and ensure you have something like this:
```properties
# It will use the REDIS_URL env variable if present, otherwise defaults to localhost
spring.data.redis.url=${REDIS_URL:redis://localhost:6379}
```

### 1.2 Update Frontend API URL
By default, the frontend hits `http://localhost:8080/api`. We need to use the production URL when deployed.
Open `frontend/src/services/api.js` and change `API_BASE_URL`:
```javascript
// Vite uses import.meta.env for environment variables
const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080/api';
```

---

## Step 2: Push to GitHub
Render connects directly to your GitHub repository.
1. Initialize a git repository at the root of your project (`c:\RateStream\RateStream`).
2. Commit all changes (make sure you don't commit `node_modules` or the `target` folder, they should be in `.gitignore`).
3. Push to a new public or private repository on GitHub.

---

## Step 3: Deploy Redis on Render
1. Log in to [Render.com](https://render.com).
2. Click **New** -> **Redis**.
3. Name it `ratestream-redis`.
4. Select the **Free** tier.
5. Click **Create Redis**.
6. Once created, copy the **Internal Redis URL** (it will look like `redis://red-xxxxxxxx:6379`). Keep this handy for the next step.

---

## Step 4: Deploy the Spring Boot Backend (via Docker)
1. Since Render no longer supports native Java environments for new services, we will use Docker. (A `Dockerfile` has been added to your `Backend` folder).
2. In the Render dashboard, click **New** -> **Web Service**.
3. Connect your GitHub repository.
4. Configure the Web Service:
   * **Name:** `ratestream-backend`
   * **Root Directory:** `Backend` *(This is important!)*
   * **Environment:** `Docker`
   * **Instance Type:** Free
5. Scroll down to **Environment Variables** and click **Add Environment Variable**:
   * **Key:** `REDIS_URL`
   * **Value:** *(Paste the Internal Redis URL you copied in Step 3)*
6. Click **Create Web Service**.
6. Wait a few minutes for the build to finish. Once live, copy your backend's public URL (e.g., `https://ratestream-backend.onrender.com`).

---

## Step 5: Deploy the React Frontend
1. In the Render dashboard, click **New** -> **Static Site**.
2. Connect the same GitHub repository.
3. Configure the Static Site:
   * **Name:** `ratestream-frontend`
   * **Root Directory:** `frontend` *(This is important!)*
   * **Build Command:** `npm install && npm run build`
   * **Publish Directory:** `dist`
4. Scroll down to **Environment Variables** and click **Add Environment Variable**:
   * **Key:** `VITE_API_BASE_URL`
   * **Value:** *(Paste the Backend public URL from Step 4)* + `/api` (e.g., `https://ratestream-backend.onrender.com/api`)
5. Click **Create Static Site**.

---

## 🎉 You're Done!

Once the frontend finishes deploying, Render will give you a public URL (e.g., `https://ratestream-frontend.onrender.com`). Click it, and you will see your RateStream dashboard live on the internet, connected securely to your backend and Redis database!
