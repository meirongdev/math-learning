package com.mathlearning.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "knowledge_nodes")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgeNode {

	@Id
	@Column(length = 100)
	private String code;

	@Column(name = "name_en", nullable = false, length = 200)
	private String nameEn;

	@Column(name = "name_zh", nullable = false, length = 200)
	private String nameZh;

	@Column(name = "parent_code", length = 100)
	private String parentCode;

	@Column(name = "grade_start", nullable = false)
	private int gradeStart;

	@Column(name = "sort_order", nullable = false)
	private int sortOrder;
}
