{{/*
Expand the name of the chart.
*/}}
{{- define "math-learning.name" -}}
{{- .Chart.Name | trunc 63 | trimSuffix "-" }}
{{- end }}

{{/*
Create a default fully qualified app name.
*/}}
{{- define "math-learning.fullname" -}}
{{- printf "%s" (include "math-learning.name" .) | trunc 63 | trimSuffix "-" }}
{{- end }}

{{/*
Common labels
*/}}
{{- define "math-learning.labels" -}}
helm.sh/chart: {{ .Chart.Name }}-{{ .Chart.Version }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
{{- end }}

{{/*
Backend selector labels
*/}}
{{- define "math-learning.backend.selectorLabels" -}}
app.kubernetes.io/name: {{ include "math-learning.name" . }}-backend
app.kubernetes.io/instance: {{ .Release.Name }}
{{- end }}

{{/*
Frontend selector labels
*/}}
{{- define "math-learning.frontend.selectorLabels" -}}
app.kubernetes.io/name: {{ include "math-learning.name" . }}-frontend
app.kubernetes.io/instance: {{ .Release.Name }}
{{- end }}
