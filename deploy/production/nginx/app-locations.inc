root /usr/share/nginx/html;
index index.html;
client_max_body_size 55m;

location /mall-api/ {
    proxy_pass http://mall:8092/;
    proxy_http_version 1.1;
    proxy_set_header Host $host;
    proxy_set_header X-Real-IP $remote_addr;
    proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    proxy_set_header X-Forwarded-Proto $scheme;
    proxy_connect_timeout 5s;
    proxy_read_timeout 30s;
}

location /chat-api/ {
    proxy_pass http://chat:8095/;
    proxy_http_version 1.1;
    proxy_set_header Host $host;
    proxy_set_header X-Real-IP $remote_addr;
    proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    proxy_set_header X-Forwarded-Proto $scheme;
    proxy_set_header Connection "";
    proxy_buffering off;
    proxy_cache off;
    proxy_connect_timeout 5s;
    proxy_read_timeout 310s;
    proxy_send_timeout 310s;
}

location = /health {
    access_log off;
    default_type text/plain;
    return 200 "ok\n";
}

location / {
    try_files $uri $uri/ /index.html;
}

add_header X-Content-Type-Options nosniff always;
add_header Referrer-Policy strict-origin-when-cross-origin always;
