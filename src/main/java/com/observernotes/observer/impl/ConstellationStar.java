package com.observernotes.observer.impl;

import com.observernotes.domain.TaskId;

/** Uma estrela da constelação: representa uma tarefa existente. */
public record ConstellationStar(TaskId taskId, boolean lit) { }
