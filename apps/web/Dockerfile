# cheesino web — online dağıtım (uygulama + proxy + ffmpeg tek serviste).
# Tek HTTPS URL: sayfayı sunar, HTTP/CORS'u çözer, MKV/AVI'yi anlık remux eder.
FROM node:20-slim

# ffmpeg → MKV/AVI oynatma (online'da da çalışsın).
RUN apt-get update && apt-get install -y --no-install-recommends ffmpeg \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app
COPY apps/web/ ./apps/web/

# Host sağlayıcı PORT'u env ile verir (Render/Railway/Fly). proxy.js okur.
ENV PORT=8088
EXPOSE 8088
CMD ["node", "apps/web/proxy.js"]
