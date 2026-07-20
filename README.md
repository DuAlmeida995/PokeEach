# PokeEach — Sistema Distribuído de Coleção e Troca de Pokémon

Plataforma **100% descentralizada (Pure P2P)** baseada em Blockchain, onde cada Pokémon é um ativo digital criptográfico único (NFT) com atributos variáveis (IVs).

---

## Tecnologias necessárias

| Tecnologia | Versão mínima | Para quê |
|:---|:---|:---|
| **Java JDK** | 17 | Compilar e rodar o backend |
| **Apache Maven** | 3.8+ | Gerenciar dependências e empacotar o JAR |
| **Node.js** | 18+ | Rodar o frontend React |
| **npm** | 9+ | Instalar dependências do frontend |

### Instalação rápida (Windows)
```powershell
winget install Microsoft.OpenJDK.17
winget install Apache.Maven
winget install OpenJS.NodeJS
```

---

## Como rodar — Mesma máquina (desenvolvimento)

### 1. Compilar o backend
```powershell
cd backend
mvn clean package -q
```

### 2. Iniciar os nós P2P

Cada nó precisa de um terminal separado. As portas seguem a convenção:
- **Porta P2P**: `808X` (comunicação entre nós)
- **Porta REST**: `900X` (comunicação com o frontend) — calculada automaticamente como `9000 + (portaP2P - 8000)`

```powershell
# Terminal 1 — Nó semente (sem vizinhos)
java -jar target\pokeeach-1.0-SNAPSHOT.jar 8081

# Terminal 2 — Segundo nó (aponta para o semente)
java -jar target\pokeeach-1.0-SNAPSHOT.jar 8082 127.0.0.1:8081

# Terminal 3 — Terceiro nó (conhece ambos)
java -jar target\pokeeach-1.0-SNAPSHOT.jar 8083 127.0.0.1:8081 127.0.0.1:8082
```

### 3. Iniciar o frontend

Para cada jogador, abra um terminal apontando para o nó dele:

```powershell
cd frontend
npm install   # apenas na primeira vez

# Jogador 1 — controla o nó 8081
$env:VITE_API_URL="http://localhost:9081"; npm run dev -- --port 3000

# Jogador 2 — controla o nó 8082
$env:VITE_API_URL="http://localhost:9082"; npm run dev -- --port 3001
```

Acesse no navegador:
- Jogador 1: `http://localhost:3000`
- Jogador 2: `http://localhost:3001`

---

## Como rodar — Computadores diferentes (rede local)

Para jogar entre máquinas diferentes na mesma rede Wi-Fi ou cabeada:

### 1. Descobrir o IP local de cada máquina

Em cada computador, abra o PowerShell e rode:
```powershell
ipconfig
```
Procure o campo **Endereço IPv4** (ex: `192.168.1.10`).

### 2. Iniciar o backend apontando para o IP do colega

**Máquina A (IP: 192.168.1.10) — nó semente:**
```powershell
java -jar target\pokeeach-1.0-SNAPSHOT.jar 8081
```

**Máquina B (IP: 192.168.1.20) — conecta na Máquina A:**
```powershell
java -jar target\pokeeach-1.0-SNAPSHOT.jar 8082 192.168.1.10:8081
```

**Máquina C (IP: 192.168.1.30) — conecta em ambas:**
```powershell
java -jar target\pokeeach-1.0-SNAPSHOT.jar 8083 192.168.1.10:8081 192.168.1.20:8082
```

### 3. Iniciar o frontend em cada máquina

Cada jogador aponta o frontend para o nó local da sua própria máquina:

**Máquina A:**
```powershell
$env:VITE_API_URL="http://localhost:9081"; npm run dev -- --port 3000 --host
```

**Máquina B:**
```powershell
$env:VITE_API_URL="http://localhost:9082"; npm run dev -- --port 3000 --host
```

> O `--host` expõe o frontend na rede local, mas cada jogador acessa `http://localhost:3000` no **seu próprio computador**.

### 4. Liberar as portas no Firewall (Windows)

Se os nós não conseguirem se conectar, é necessário liberar as portas no firewall de cada máquina. Execute como **Administrador**:

```powershell
# Libera portas P2P
New-NetFirewallRule -DisplayName "PokeEach P2P 8081" -Direction Inbound -Protocol TCP -LocalPort 8081 -Action Allow
New-NetFirewallRule -DisplayName "PokeEach P2P 8082" -Direction Inbound -Protocol TCP -LocalPort 8082 -Action Allow
New-NetFirewallRule -DisplayName "PokeEach P2P 8083" -Direction Inbound -Protocol TCP -LocalPort 8083 -Action Allow

# Libera portas REST
New-NetFirewallRule -DisplayName "PokeEach REST 9081" -Direction Inbound -Protocol TCP -LocalPort 9081 -Action Allow
New-NetFirewallRule -DisplayName "PokeEach REST 9082" -Direction Inbound -Protocol TCP -LocalPort 9082 -Action Allow
New-NetFirewallRule -DisplayName "PokeEach REST 9083" -Direction Inbound -Protocol TCP -LocalPort 9083 -Action Allow
```

---

## Como jogar

### Minerando um Pokémon
Clique no botão **Minerar** na barra lateral. O sistema executa o algoritmo Proof of Work, sorteia um Pokémon deterministicamente a partir do hash do bloco e gera IVs únicos consultando os stats base na PokéAPI. O bloco é propagado para todos os nós da rede via Gossip Protocol.

### Trocando Pokémon
1. No **Inventário**, selecione o Pokémon que deseja oferecer
2. Clique em **Trocar Pokémon** 
3. Selecione um treinador online na barra lateral
4. Escolha o Pokémon dele que deseja receber
5. Clique em **Confirmar Troca**, uma solicitação é enviada ao rival
6. O rival verá uma notificação e poderá **Aceitar** ou **Recusar**
7. Se aceita, duas transações são criadas e um bloco é minerado automaticamente para confirmá-las

---

## Equipe (Grupo 3)

- André Portela - 15634885
- Davi Oliveira - 15648741
- Eduardo Almeida - 15526004 
- Júlio Arroio - 15466241 
