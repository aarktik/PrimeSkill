"""Deployment configuration checks; does not replace an OCI build."""
import pathlib
import json
import unittest
ROOT = pathlib.Path(__file__).resolve().parents[1]
class VercelConfigurationTests(unittest.TestCase):
    def test_public_routes_resolve_to_an_existing_container(self):
        config = json.loads((ROOT / 'vercel.json').read_text())
        route = next(r for r in config['rewrites'] if r['source'] == '/(.*)')
        service = config['services'][route['destination']['service']]
        self.assertEqual('container', service['runtime'])
        service_root = (ROOT / service['root']).resolve()
        self.assertEqual(ROOT, service_root)
        self.assertTrue((service_root / service['entrypoint']).is_file())
        for key in ('buildCommand', 'outputDirectory', 'installCommand', 'framework'):
            self.assertNotIn(key, config, 'Top-level build overrides conflict with services mode')
    def test_java17_nonroot_port_and_signal(self):
        text = (ROOT / 'Dockerfile.vercel').read_text()
        self.assertIn('eclipse-temurin:17-jre', text)
        self.assertIn('USER 10001:10001', text)
        self.assertIn('exec java', text)
        self.assertIn('${PORT:-8080}', text)
        self.assertIn('SPRING_PROFILES_ACTIVE=vercel', text)
        self.assertNotIn('ARG SUPABASE', text)
        self.assertNotIn('COPY . ', text)
    def test_production_sql_init_is_disabled(self):
        text = (ROOT / 'code/src/main/resources/application-vercel.properties').read_text()
        for setting in ('spring.sql.init.mode=never', 'spring.jpa.hibernate.ddl-auto=validate',
                        'server.servlet.session.cookie.secure=true', 'server.forward-headers-strategy=framework',
                        'springdoc.api-docs.enabled=false', 'springdoc.swagger-ui.enabled=false'):
            self.assertIn(setting, text)
if __name__ == '__main__': unittest.main()
