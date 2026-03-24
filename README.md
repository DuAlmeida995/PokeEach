 # Sistema Distribuído de Coleção e Troca de Pokémon (NFTs)


## Sobre o Projeto

O sistema implementa uma infraestrutura distribuída inspirada na dinâmica de colecionismo de Pokémon e seu objetivo principal é construir uma arquitetura descentralizada e tolerante a falhas onde cada Pokémon atua como um ativo digital único (NFT) com atributos variáveis (IVs). Os usuários podem interagir em um chat global em tempo real, competir por capturas (spawns aleatórios) e realizar trocas (P2P) de forma segura através do "PC", um sistema de armazenamento persistente e distribuído.

---

## Arquitetura de Sistemas Distribuídos

Para garantir que o sistema não dependa de um servidor centralizado sujeito a ponto único de falha, a arquitetura foi dividida em dois grandes subsistemas, aplicando os principais conceitos da literatura de Sistemas Distribuídos:

### 1. Comunicação Assíncrona: Chat Global e Spawns (Pub/Sub)
A dinâmica de tempo real do jogo foi isolada do núcleo transacional pesado.
* **Middleware:** Utilizamos o **Redis Pub/Sub** para gerenciar a comunicação.
* **Funcionamento:** Quando a lógica do backend decide gerar um Pokémon selvagem, o evento é publicado em um tópico global. Todos os nós da rede (e por consequência, os clientes conectados a eles) recebem a notificação simultaneamente e de forma ordenada. Isso garante alta performance e baixo acoplamento entre os serviços.

### 2. Nomeação e Armazenamento: O "PC" (P2P Estruturado)
O inventário dos jogadores não fica em um banco de dados relacional clássico.
* **Modelo:** Implementação de uma Tabela de Hash Distribuída (**DHT**) baseada no algoritmo **Chord**.
* **Funcionamento:** A rede forma um anel lógico. Cada Pokémon capturado recebe um identificador único (Hash) e é armazenado no nó responsável por aquele segmento do anel. Isso resolve o problema de Nomeação e garante que a busca por um ativo ocorra em complexidade logarítmica, distribuindo a carga de armazenamento entre as máquinas.

### 3. Coordenação e Exclusão Mútua: Capturas e Trocas
As transações críticas do jogo exigem garantias rígidas de consistência.
* **Concorrência de Captura:** Se múltiplos jogadores tentarem capturar o mesmo Pokémon gerado no chat no mesmo milissegundo, o sistema utiliza algoritmos de **Exclusão Mútua Distribuída** (Distributed Locks) para garantir a atomicidade da operação. Apenas a primeira requisição adquire o bloqueio, evitando a duplicação de ativos.
* **Trocas P2P (Trade):** Durante a troca direta de ativos entre dois jogadores em nós diferentes, o sistema coordena uma transação segura. Caso ocorra uma queda de rede em qualquer um dos nós durante o processo, a transação sofre *rollback*, prevenindo a clonagem ou perda de Pokémon.

---

## Tecnologias Utilizadas

* **Backend / Nós P2P:** Java 
* **Mensageria / Tempo Real:** Redis (Pub/Sub).
* **Frontend:** A definir
* **Comunicação entre Nós:** Sockets / gRPC.

---

## Como Executar o Projeto

---

## Equipe de Desenvolvimento
Projeto desenvolvido por:

* **André Portela** -
* * **Davi Oliveira** -  
* **Eduardo Almeida** - 
* **Júlio Arroio** -

