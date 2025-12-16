# Guide de Déploiement - Garbage Collector

## Prérequis

```bash
sudo apt update
sudo apt install nginx openjdk-17-jdk
```

---

## Étape 1 : Build du Frontend Angular

```bash
cd /home/omarsafi/garbage-collector-frontend
npm install
npm run build
```

Les fichiers compilés se trouvent dans : `dist/garbage-collector-frontend/browser/`

---

## Étape 2 : Copier le Frontend vers Nginx

```bash
sudo mkdir -p /var/www/garbage-collector
sudo cp -r dist/garbage-collector-frontend/browser/* /var/www/garbage-collector/
```

---

## Étape 3 : Build du Backend Spring Boot

```bash
cd /home/omarsafi/IdeaProjects/GarbageCollectorBackend
./mvnw clean package -DskipTests
```

Le fichier JAR se trouve dans : `target/GarbageCollectorBackend-0.0.1-SNAPSHOT.jar`

---

## Étape 4 : Configurer Nginx

Créer le fichier de configuration :

```bash
sudo nano /etc/nginx/sites-available/garbage-collector
```

Coller cette configuration :

```nginx
server {
    listen 80;
    server_name localhost;

    # Frontend Angular
    root /var/www/garbage-collector;
    index index.html;

    # Routes Angular (SPA)
    location / {
        try_files $uri $uri/ /index.html;
    }

    # API Backend
    location /api/ {
        proxy_pass http://127.0.0.1:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
    }

    # WebSocket
    location /ws {
        proxy_pass http://127.0.0.1:8080;
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection "upgrade";
    }
}
```

Activer le site :

```bash
sudo ln -s /etc/nginx/sites-available/garbage-collector /etc/nginx/sites-enabled/
sudo rm /etc/nginx/sites-enabled/default
sudo nginx -t
sudo systemctl restart nginx
```

---

## Étape 5 : Lancer le Backend

```bash
cd /home/omarsafi/IdeaProjects/GarbageCollectorBackend
java -jar target/GarbageCollectorBackend-0.0.1-SNAPSHOT.jar
```

---

## Étape 6 : Tester

- Frontend : http://localhost
- API : http://localhost/api/departments

---

## Architecture

```
┌─────────────────────────────────────────────────────────┐
│                      NAVIGATEUR                          │
│                  http://localhost                        │
└─────────────────────┬───────────────────────────────────┘
                      │
                      ▼
┌─────────────────────────────────────────────────────────┐
│                       NGINX                              │
│                    (Port 80)                             │
│                                                          │
│  /              → Fichiers Angular (HTML, JS, CSS)       │
│  /api/*         → Proxy vers Backend (Port 8080)         │
│  /ws            → WebSocket vers Backend                 │
└─────────────────────┬───────────────────────────────────┘
                      │
          ┌───────────┴───────────┐
          ▼                       ▼
┌─────────────────┐     ┌─────────────────┐
│  Fichiers       │     │  Spring Boot    │
│  Statiques      │     │  Backend        │
│  Angular        │     │  (Port 8080)    │
│                 │     │                 │
│ /var/www/       │     │  API REST       │
│ garbage-        │     │  WebSocket      │
│ collector/      │     │  JWT Auth       │
└─────────────────┘     └────────┬────────┘
                                 │
                    ┌────────────┴────────────┐
                    ▼                         ▼
            ┌─────────────┐           ┌─────────────┐
            │  MongoDB    │           │   Redis     │
            │  (27017)    │           │   (6379)    │
            └─────────────┘           └─────────────┘
```

---

## Explication pour le Professeur

**Nginx** agit comme un **reverse proxy** :

1. **Requêtes statiques** (`/`) : Nginx sert directement les fichiers Angular (HTML, JS, CSS) depuis `/var/www/garbage-collector`

2. **Requêtes API** (`/api/*`) : Nginx redirige vers le backend Spring Boot sur le port 8080

3. **WebSocket** (`/ws`) : Nginx établit une connexion persistante pour le suivi temps réel

**Avantages de cette architecture** :
- Point d'entrée unique (port 80)
- Séparation frontend/backend
- Cache des fichiers statiques
- Possibilité d'ajouter HTTPS facilement

---

## (Optionnel) Sécuriser l'accès avec une authentification Basic Nginx

1. **Créer un fichier de mots de passe** (exemple pour l'utilisateur `admin`) :

```bash
sudo apt install apache2-utils  # si htpasswd n'est pas installé
sudo htpasswd -c /etc/nginx/.htpasswd admin
```

2. **Modifier la configuration Nginx** (`/etc/nginx/sites-available/garbage-collector`) :

```nginx
    # API Backend
    location /api/ {
        auth_basic "Restricted API";
        auth_basic_user_file /etc/nginx/.htpasswd;
        proxy_pass http://127.0.0.1:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
    }
```

3. **Redémarrer Nginx** :

```bash
sudo nginx -t
sudo systemctl reload nginx
```

L'accès à l'API sera alors protégé par un login/mot de passe HTTP Basic.
