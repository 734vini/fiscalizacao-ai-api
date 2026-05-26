# 🤖 Fiscalização Urbana — AI Status API (Java)

API Java/Spring Boot que recebe uma ocorrência urbana e usa IA (Claude/Anthropic) para gerar uma resposta institucional como se fosse a Prefeitura Municipal, mantendo histórico para coesão nas respostas.

---

## 🏗️ Arquitetura

```
app-fiscalizacao-urbana (Next.js)
        │
        │  GET /api/ai/evaluate?occurrenceId=42&status=EM_ANDAMENTO
        ▼
fiscalizacao-ai-api (Java / Spring Boot)  ◄── este projeto
        │
        ├── Consulta histórico local (H2/PostgreSQL)
        ├── Monta contexto completo (ocorrência + histórico)
        ├── Chama Anthropic API (Claude)
        ├── Persiste resultado
        └── Retorna { status, isNew, prefeituraMessage, history }
```

---

## 🚀 Como rodar

### Pré-requisitos
- Java 21+
- Maven 3.9+
- Chave de API da Anthropic (`ANTHROPIC_API_KEY`)

### Subir localmente

```bash
# 1. Clone e configure
git clone <este-repo>
cd fiscalizacao-ai-api

# 2. Defina sua chave da Anthropic
export ANTHROPIC_API_KEY=sk-ant-...

# 3. Rode
./mvnw spring-boot:run

# Servidor inicia em http://localhost:8080
```

### Variáveis de ambiente

| Variável | Obrigatória | Descrição |
|---|---|---|
| `ANTHROPIC_API_KEY` | ✅ | Chave da API Anthropic |
| `FISCALIZACAO_BACKEND_URL` | ❌ | URL do app Next.js (padrão: localhost:3000) |
| `CORS_ORIGINS` | ❌ | Origins permitidos (padrão: localhost:3000,3001) |

---

## 📡 Endpoints

### `GET /api/ai/evaluate` — Avaliar ocorrência (modo simples)

Usado diretamente pelo botão "Atualizar Status" no front-end.

**Query params:**

| Param | Tipo | Obrigatório | Descrição |
|---|---|---|---|
| `occurrenceId` | Integer | ✅ | ID da ocorrência |
| `status` | String | ✅ | Novo status desejado |
| `title` | String | ❌ | Título da ocorrência (enriquece contexto da IA) |
| `description` | String | ❌ | Descrição |
| `category` | String | ❌ | Categoria |
| `city` | String | ❌ | Cidade |
| `priority` | Integer | ❌ | Prioridade 1-5 |
| `currentStatus` | String | ❌ | Status atual antes da mudança |

**Exemplo:**
```
GET http://localhost:8080/api/ai/evaluate?occurrenceId=42&status=EM_ANDAMENTO&title=Buraco+na+Rua+XV&category=Infraestrutura&city=Itajaí&currentStatus=ABERTO
```

**Resposta:**
```json
{
  "success": true,
  "code": "EVALUATION_SUCCESS",
  "message": "Avaliação concluída com sucesso",
  "data": {
    "occurrenceId": 42,
    "status": "EM_ANDAMENTO",
    "isNew": true,
    "prefeituraMessage": "A Prefeitura Municipal de Itajaí informa que a ocorrência referente ao buraco na Rua XV foi recebida e uma equipe de infraestrutura já foi designada para atendimento.",
    "totalEvaluations": 1,
    "history": []
  },
  "timestamp": "2026-05-23T10:30:00"
}
```

---

### `POST /api/ai/evaluate` — Avaliar ocorrência (modo completo)

Mesmo resultado, mas com body JSON — preferível quando o front-end tem acesso a todos os dados.

```json
POST http://localhost:8080/api/ai/evaluate
Content-Type: application/json

{
  "occurrenceId": 42,
  "status": "RESOLVIDO",
  "title": "Buraco na Rua XV",
  "description": "Buraco grande causando risco a veículos",
  "category": "Infraestrutura",
  "city": "Itajaí",
  "priority": 4,
  "currentStatus": "EM_ANDAMENTO"
}
```

---

### `GET /api/ai/history` — Histórico de avaliações

Retorna todas as avaliações anteriores de uma ocorrência.

```
GET http://localhost:8080/api/ai/history?occurrenceId=42
```

---

### `GET /api/ai/health` — Health check

```
GET http://localhost:8080/api/ai/health
→ { "status": "UP", "timestamp": "..." }
```

---

## 🔗 Integração com app-fiscalizacao-urbana (Next.js)

### No front-end, ao clicar em "Atualizar Status":

```typescript
// Exemplo de integração no front-end Next.js
async function updateOccurrenceStatus(occurrence: Occurrence, newStatus: string) {
  // 1. Chama a AI API para obter avaliação da prefeitura
  const aiResponse = await fetch(
    `http://localhost:8080/api/ai/evaluate?` +
    new URLSearchParams({
      occurrenceId: String(occurrence.id),
      status: newStatus,
      title: occurrence.title,
      category: occurrence.category?.name ?? '',
      city: occurrence.city ?? '',
      priority: String(occurrence.priority),
      currentStatus: occurrence.status,
    })
  );
  const aiData = await aiResponse.json();

  // 2. Usa o status final retornado pela IA
  const finalStatus = aiData.data.status;
  const prefeituraMessage = aiData.data.prefeituraMessage;

  // 3. Atualiza no backend Next.js com o status validado pela IA
  await fetch(`/api/occurrences?id=${occurrence.id}`, {
    method: 'PATCH',
    headers: {
      'Content-Type': 'application/json',
      'Authorization': `Bearer ${token}`,
    },
    body: JSON.stringify({ status: finalStatus }),
  });

  // 4. Exibe mensagem da prefeitura para o usuário
  showNotification(prefeituraMessage);
  
  return { finalStatus, prefeituraMessage, isNew: aiData.data.isNew };
}
```

---

## 🗄️ Banco de dados

O serviço mantém sua **própria base de dados** com o histórico de avaliações.

**Tabela `occurrence_evaluations`:**

| Coluna | Tipo | Descrição |
|---|---|---|
| `id` | BIGINT PK | ID auto-incremento |
| `occurrence_id` | INTEGER | ID da ocorrência no app Next.js |
| `previous_status` | VARCHAR | Status antes da mudança |
| `requested_status` | VARCHAR | Status solicitado pelo usuário |
| `final_status` | VARCHAR | Status aprovado pela IA |
| `ai_message` | VARCHAR(1000) | Mensagem gerada pela IA |
| `is_new_occurrence` | BOOLEAN | Se era a primeira avaliação |
| `occurrence_title` | VARCHAR | Título (snapshot) |
| `occurrence_category` | VARCHAR | Categoria (snapshot) |
| `evaluated_at` | TIMESTAMP | Data/hora da avaliação |

Em **desenvolvimento** usa H2 (arquivo local em `./data/`).  
Em **produção**, configure PostgreSQL via variáveis de ambiente.

Console H2 disponível em: http://localhost:8080/h2-console  
JDBC URL: `jdbc:h2:file:./data/fiscalizacao-ai`

---

## 🧠 Como a IA avalia

O Claude recebe um prompt com:
1. Dados da ocorrência (título, descrição, categoria, cidade, prioridade)
2. Transição de status (anterior → novo)
3. Histórico das últimas 5 avaliações desta ocorrência

E retorna JSON:
```json
{ "status": "EM_ANDAMENTO", "message": "Mensagem institucional da prefeitura." }
```

**Status válidos:** `ABERTO`, `EM_ANDAMENTO`, `RESOLVIDO`, `FECHADO`, `REJEITADO`

A IA pode **recusar transições incoerentes** (ex: RESOLVIDO → ABERTO sem justificativa) e sugerir o status mais adequado.

---

## 📦 Build para produção

```bash
./mvnw clean package -DskipTests
java -jar target/fiscalizacao-ai-api-1.0.0.jar
```

Para **PostgreSQL em produção**, edite `application.properties` ou use variáveis de ambiente.
