 # Plataforma Distribuída de Processamento Colaborativo de Tarefas

## 📋 Sobre o Projeto

Sistema distribuído para orquestração de tarefas, permitindo a submissão de trabalhos por clientes, distribuição para múltiplos nós de processamento (workers), acompanhamento do estado global e recuperação em caso de falhas.

### 🎯 Objetivo

Desenvolver uma plataforma distribuída que simula um sistema real de processamento colaborativo, aplicando conceitos centrais de sistemas distribuídos:
- Balanceamento de carga
- Consistência de estado
- Tolerância a falhas
- Replicação
- Comunicação entre processos
- Autenticação

## 📦 Componentes do Sistema

| Componente | Descrição | Porta |
|------------|-----------|-------|
| **Orquestrador Principal** | Coordena tarefas, distribui workers, mantém estado | 8080 (workers), 8081 (clientes) |
| **Orquestrador Backup** | Mantém cópia sincronizada, assume falhas | 8081 (failover) |
| **Workers** | Executam tarefas, enviam heartbeat | 9001, 9002, 9003 |
| **Cliente** | Interface para submeter e consultar tarefas | - |

## 🏛️ Arquitetura
```bash
┌─────────────┐     ┌─────────────────────────────────────┐     ┌─────────────┐
│   Cliente   │────▶│                                   │◀────│   Cliente   │
└─────────────┘     │   Orquestrador Principal (Porta 8081) │   └─────────────┘
                    │                                   │
┌─────────────┐     │   🔄 UDP Multicast (230.0.0.1:8888)   │  ┌─────────────┐
│   Cliente   │────▶│                                   │◀────│   Cliente   │
└─────────────┘     └─────────────────────────────────────┘    └─────────────┘
                                    │
                                    ▼
                    ┌─────────────────────────────────────┐
                    │   Orquestrador Backup (Porta 8081)  │
                    │   (Assume em caso de falha)         │
                    └─────────────────────────────────────┘
                                    │
                    ┌───────────────┼───────────────┐
                    ▼               ▼               ▼
            ┌───────────┐   ┌───────────┐   ┌───────────┐
            │  Worker 1 │   │  Worker 2 │   │  Worker 3 │
            │ (Porta    │   │ (Porta    │   │ (Porta    │
            │  9001)    │   │  9002)    │   │  9003)    │
            └───────────┘   └───────────┘   └───────────┘
```



## 🔧 Requisitos

- **Java 11** ou superior
- **Git** (para clonar o repositório)
- **Jackson** (bibliotecas na pasta `lib/`)

### 📁 Estrutura do Projeto
```bash
plataforma-distribuida/
├── src/
│ ├── common/
│ │ ├── models/ # Task, WorkerInfo, TaskResult, TaskStatus
│ │ ├── util/ # Logger, JsonUtil
│ │ └── protocol/ # MessageProtocol
│ ├── orchestrator/
│ │ ├── PrimaryOrchestrator.java
│ │ ├── SecondaryOrchestrator.java
│ │ └── primary/
│ │ ├── WorkerHandler.java
│ │ ├── ClientHandler.java
│ │ └── LoadBalancer.java
│ ├── worker/
│ │ └── WorkerNode.java
│ └── client/
│ └── ClientApp.java
├── lib/ # Bibliotecas JAR (Jackson)
├── logs/ # Logs gerados pelo sistema
├── bin/ # Arquivos compilados
└── README.md
```

## 🚀 Instalação e Execução
1. Clone o repositório

```bash
git clone https://github.com/seu-usuario/plataforma-distribuida.git
cd plataforma-distribuida
```

2. Baixe as bibliotecas necessárias

Coloque os seguintes JARs na pasta lib/:(

jackson-annotations-2.15.2.jar

jackson-core-2.15.2.jar

jackson-databind-2.15.2.jar

3. Compile o projeto

```bash
# Criar pasta para os arquivos compilados
mkdir -p bin
```
# Compilar todos os arquivos

```bash
javac -cp "lib/*" -d bin src/common/models/*.java src/common/util/*.java src/common/protocol/*.java src/orchestrator/*.java src/worker/*.java src/client/*.java
```
4. Execute os componentes
   
Importante: Abra terminais separados para cada componente!

Terminal 1 - Orquestrador Principal
```bash
java -cp "bin;lib/*" orchestrator.PrimaryOrchestrator
```
Terminal 2 - Orquestrador Backup
```bash
java -cp "bin;lib/*" orchestrator.SecondaryOrchestrator
```
Terminal 3 - Worker 1
```bash
java -cp "bin;lib/*" worker.WorkerNode worker1 localhost 8080 9001
```
Terminal 4 - Worker 2
```bash
java -cp "bin;lib/*" worker.WorkerNode worker2 localhost 8080 9002
```
Terminal 5 - Worker 3
```bash
java -cp "bin;lib/*" worker.WorkerNode worker3 localhost 8080 9003
```
Terminal 6 - Cliente
```bash
java -cp "bin;lib/*" client.ClientApp localhost 8081
```
Como Usar o Cliente:

# Credenciais de Acesso

aluno1	senha123
      ou
aluno2	senha456

# Comandos Disponíveis
```bash
login                                    Autenticar (primeiro acesso)	usuário: aluno1, senha: senha123
submit <descrição> <tempo_ms>	           Submeter nova tarefa	submit Calcular media 3000
status <id>	                             Consultar status da tarefa	status abc-123-def
logout	                                 Sair da sessão	logout
help	                                   Mostrar ajuda	help
```
Exemplo de Sessão
```bash
=== Plataforma Distribuída de Processamento ===
Conectado ao servidor localhost:8081

[LOGIN] Usuário: aluno1
[LOGIN] Senha: senha123
✅ Login realizado com sucesso!

=== MENU ===
submit <descrição> <tempo_ms> - Submeter tarefa
status <id> - Consultar status da tarefa
logout - Sair
===============

> submit Calcular media 3
✅ Tarefa submetida com sucesso!
   ID: a1b2c3d4-e5f6-7890-abcd-ef1234567890
   Descrição: Calcular media
   Tempo estimado: 3000ms

> status a1b2c3d4-e5f6-7890-abcd-ef1234567890
📊 Status: ID: a1b2c3d4... | Descrição: Calcular media | Status: Concluída | Worker: worker2

> logout
✅ Logout realizado!
```

# Testes e Cenários
Teste 1: Submissão de Múltiplas Tarefas
```bash
> submit Tarefa 1 2
✅ Tarefa submetida! ID: task-001
> submit Tarefa 2 3
✅ Tarefa submetida! ID: task-002
> submit Tarefa 3 1
✅ Tarefa submetida! ID: task-003
```

# Teste 2: Balanceamento de Carga
Com 3 workers ativos, as tarefas são distribuídas automaticamente:

Tarefa 1 → worker1 (carga 0 → 1)

Tarefa 2 → worker2 (carga 0 → 1)

Tarefa 3 → worker3 (carga 0 → 1)

Tarefa 4 → worker1 (menor carga)

Teste 3: Falha de Worker e Reatribuição
Submeta uma tarefa longa (ex: 30000ms)

Mate o worker que está processando (Ctrl+C)

O orquestrador detecta a falha após 10s

A tarefa é reatribuída para outro worker

Logs esperados:
```bash
text
[WARN] [PrimaryOrchestrator] Worker worker1 falhou!
[INFO] [PrimaryOrchestrator] Tarefa xxx REATRIBUIDA do worker worker1
[INFO] [PrimaryOrchestrator] Tarefa xxx distribuída para worker2
Teste 4: Failover do Orquestrador
Mate o Orquestrador Principal (Ctrl+C)
```

Aguarde 10 segundos

O Backup assume automaticamente

O cliente pode consultar tarefas normalmente

Logs esperados:

text
```bash
[WARN] [SecondaryOrchestrator] Primário falhou! Iniciando failover...
[INFO] [SecondaryOrchestrator] ===== ASSUMINDO COMO ORQUESTRADOR PRINCIPAL =====
[INFO] [SecondaryOrchestrator] Servidor de clientes iniciado na porta 8081
```

# Balanceamento de Carga
Estratégia Escolhida: LEAST LOAD (Menor Carga)
Como funciona:

Cada worker mantém um contador de tarefas em execução

O orquestrador sempre escolhe o worker com menor carga

Em caso de empate, usa Round Robin entre os empatados

Vantagens:

Distribui tarefas baseado na carga atual

Evita sobrecarregar workers ocupados

Melhor utilização dos recursos

Limitações:

Requer monitoramento constante da carga

Não considera capacidade diferente entre workers

Logs
Os logs são salvos na pasta logs/ com o formato:
```bash
text
logs/PrimaryOrchestrator_20260330.log
logs/Worker-worker1_20260330.log
logs/ClientApp_20260330.log
```

# Eventos Registrados
```bash
Evento	                    Descrição
TASK_SUBMITTED	            Cliente submeteu nova tarefa
TASK_DISTRIBUTED	          Tarefa atribuída a um worker
TASK_COMPLETED	            Tarefa concluída com sucesso
TASK_FAILED	                Tarefa falhou na execução
TASK_REASSIGNED	            Tarefa reatribuída após falha
WORKER_FAILURE	            Worker falhou (heartbeat timeout)
WORKER_REGISTERED	          Novo worker registrado
AUTH_SUCCESS	              Login bem-sucedido
AUTH_FAILURE	              Tentativa de login inválida
FAILOVER	                  Backup assumiu como primário
```

## Resolução de Problemas
```bash
Erro: package com.fasterxml.jackson.databind does not exist
```
Solução: Baixe os JARs do Jackson e coloque na pasta lib/, ou use a versão do JsonUtil sem dependências externas.
```bash
Erro: Address already in use
```
Solução: Algum componente já está rodando. Feche todos os terminais e reinicie.
```bash
Erro: Connection refused
```
Solução: Verifique se o orquestrador está rodando antes de iniciar workers e clientes.

## Autores

Aluno 1 - Bruno Amorim Santos

Aluno 2 - Erick Borges dos Santos

Aluno 3 - Tiago Passos

Disciplina: Sistemas Distribuídos | Docente: Felipe Silva
Instituição: IFBA - Campus Santo Antônio de Jesus
Data: Março/2026
