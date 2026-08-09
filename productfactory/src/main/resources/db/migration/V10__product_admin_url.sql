-- URL van de admin-/beheerpagina van een product. Net als target_repository_name en acceptance_url is
-- dit een vast gegeven dat niet via het dashboard wijzigt; het wordt bijgehouden in projects.yaml en
-- door nl.vdzon.productfactory.product.ProjectsYamlReconciler bij het opstarten toegepast.
alter table product_definition add column admin_url varchar(1000);
