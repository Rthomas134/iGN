/*
 * Git module gateway config UI (Ignition 8.3 React/WebUI framework).
 *
 * Built to a UMD bundle (see ../webpack.config.js) and mounted via GatewayHook's
 * getMountedResourceFolder(), matching the convention from Ignition's own
 * "webui-webpage" SDK example. The exported property name must match the componentId
 * passed to Section.PageBuilder#mount() in GatewayHook.
 *
 * Structure mirrors the 8.1 Wicket UI: a single "Projects" table is the top-level page;
 * managing a project's users is a drill-down from that project's row (there is no separate
 * top-level "Users" page), and the user form shows the SSH Key field or the Git User
 * Name/Password fields depending on whether the parent project's URI is SSH or HTTP, the
 * same way GitReposUsersPage#setupPanel() did in 8.1.
 *
 * Talks to the REST CRUD endpoints registered by GitConfigRoutes under this module's
 * /data/{moduleId}/... base path.
 */
var React = require('react');

var API_BASE = '/data/com.axone_io.ignition.git';
var MUTATING_METHODS = { POST: true, PUT: true, DELETE: true, PATCH: true };

// The gateway's data routes require an X-CSRF-Token header on mutating requests, checked
// against the token issued for the current web-ui session (see /data/app/session). Fetched
// once and cached for the life of the page.
var csrfTokenPromise = null;
function getCsrfToken() {
    if (!csrfTokenPromise) {
        csrfTokenPromise = fetch('/data/app/session', { credentials: 'same-origin' })
            .then(function (resp) { return resp.json(); })
            .then(function (session) { return session.csrfToken; });
    }
    return csrfTokenPromise;
}

function apiFetch(path, options) {
    options = options || {};
    var method = (options.method || 'GET').toUpperCase();
    var needsCsrf = MUTATING_METHODS[method];

    return (needsCsrf ? getCsrfToken() : Promise.resolve(null)).then(function (csrfToken) {
        var headers = Object.assign({ 'Content-Type': 'application/json' }, options.headers);
        if (csrfToken) headers['X-CSRF-Token'] = csrfToken;

        return fetch(API_BASE + path, Object.assign({ credentials: 'same-origin' }, options, { headers: headers }));
    }).then(function (resp) {
        if (!resp.ok) {
            return resp.text().then(function (text) {
                throw new Error('Request failed (' + resp.status + '): ' + text);
            });
        }
        var contentType = resp.headers.get('content-type') || '';
        return contentType.indexOf('json') >= 0 ? resp.json() : resp.text();
    });
}

/* ---- Shared styling, aiming for the look of Ignition's native config tables/forms ---- */

var styles = {
    page: { fontFamily: '"Noto Sans", "Segoe UI", Arial, sans-serif', color: '#333', maxWidth: 960 },
    headerRow: { display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 12 },
    title: { fontSize: 20, fontWeight: 600, margin: 0, color: '#222' },
    breadcrumb: { fontSize: 13, color: '#666', marginBottom: 4 },
    breadcrumbLink: { color: '#1a6bb5', cursor: 'pointer', textDecoration: 'none' },
    primaryButton: {
        background: '#1a6bb5', color: '#fff', border: 'none', borderRadius: 3,
        padding: '7px 16px', fontSize: 13, fontWeight: 600, cursor: 'pointer'
    },
    secondaryButton: {
        background: '#fff', color: '#444', border: '1px solid #c7c9cc', borderRadius: 3,
        padding: '7px 16px', fontSize: 13, cursor: 'pointer'
    },
    table: { width: '100%', borderCollapse: 'collapse', fontSize: 13, marginBottom: 20, background: '#fff' },
    th: {
        textAlign: 'left', padding: '9px 12px', background: '#f4f5f6', color: '#555',
        fontWeight: 600, borderBottom: '2px solid #dde0e3'
    },
    td: { padding: '9px 12px', borderBottom: '1px solid #ececec', verticalAlign: 'middle' },
    linkAction: { color: '#1a6bb5', cursor: 'pointer', fontSize: 13, background: 'none', border: 'none', padding: 0 },
    linkActionDanger: { color: '#b3261e', cursor: 'pointer', fontSize: 13, background: 'none', border: 'none', padding: 0 },
    panel: { background: '#fafbfc', border: '1px solid #e2e4e7', borderRadius: 4, padding: 18, marginTop: 8 },
    panelTitle: { fontSize: 15, fontWeight: 600, margin: '0 0 14px 0', color: '#222' },
    field: { display: 'flex', flexDirection: 'column', fontSize: 12, color: '#555', marginBottom: 12, maxWidth: 360 },
    label: { marginBottom: 4, fontWeight: 600 },
    required: { color: '#b3261e' },
    input: { padding: '6px 8px', border: '1px solid #c7c9cc', borderRadius: 3, fontSize: 13 },
    formActions: { display: 'flex', gap: 8, marginTop: 4 },
    errorBanner: { background: '#fdecea', color: '#611a15', padding: '8px 12px', marginBottom: 14, borderRadius: 3, fontSize: 13 },
    emptyRow: { padding: '14px 12px', color: '#888', fontStyle: 'italic' }
};

function useErrorBanner() {
    var state = React.useState(null);
    var error = state[0], setError = state[1];
    var banner = error ? React.createElement('div', { style: styles.errorBanner }, error) : null;
    return [banner, setError];
}

function Field(props) {
    return React.createElement('label', { style: styles.field },
        React.createElement('span', { style: styles.label },
            props.label, props.required ? React.createElement('span', { style: styles.required }, ' *') : null),
        React.createElement('input', {
            type: props.type || 'text',
            style: styles.input,
            value: props.value,
            disabled: !!props.disabled,
            required: !!props.required,
            min: props.min,
            onChange: function (e) { props.onChange(e.target.value); }
        })
    );
}

/* ---------------------------------- Projects list ---------------------------------- */

function ProjectsList(props) {
    var projects = props.projects;

    return React.createElement('table', { style: styles.table },
        React.createElement('thread', null,
            React.createElement('tr', null,
                React.createElement('th', { style: styles.th }, 'Project Name'),
                React.createElement('th', { style: styles.th }, 'Repository URI'),
                React.createElement('th', { style: styles.th }, 'Auth'),
                React.createElement('th', { style: styles.th }, '')
            )
        ),
        React.createElement('body', null,
            projects.length === 0
                ? React.createElement('tr', null, React.createElement('td', { style: styles.emptyRow, colSpan: 4 }, 'No git projects configured yet.'))
                : projects.map(function (p) {
                    return React.createElement('tr', { key: p.id },
                        React.createElement('td', { style: styles.td }, p.projectName),
                        React.createElement('td', { style: styles.td }, p.uri),
                        React.createElement('td', { style: styles.td }, p.sshAuthentication ? 'SSH' : 'HTTP'),
                        React.createElement('td', { style: styles.td },
                            React.createElement('button', { style: styles.linkAction, onClick: function () { props.onManageUsers(p); } }, 'Manage Users'),
                            ' | ',
                            React.createElement('button', { style: styles.linkAction, onClick: function () { props.onEdit(p); } }, 'Edit'),
                            ' | ',
                            React.createElement('button', { style: styles.linkActionDanger, onClick: function () { props.onDelete(p); } }, 'Delete')
                        )
                    );
                })
        )
    );
}

function ProjectForm(props) {
    var form = props.form, setForm = props.setForm;
    return React.createElement('div', { style: styles.panel },
        React.createElement('h3', { style: styles.panelTitle }, props.editing ? 'Edit Project' : 'New Project'),
        React.createElement('form', { onSubmit: props.onSubmit },
            React.createElement(Field, {
                label: 'Project Name', required: true, value: form.projectName,
                onChange: function (v) { setForm(Object.assign({}, form, { projectName: v })); }
            }),
            React.createElement(Field, {
                label: 'Repository URI', required: true, value: form.uri,
                onChange: function (v) { setForm(Object.assign({}, form, { uri: v })); }
            }),
            React.createElement('div', { style: styles.formActions },
                React.createElement('button', { type: 'submit', style: styles.primaryButton }, props.editing ? 'Save' : 'Add Project'),
                React.createElement('button', { type: 'button', style: styles.secondaryButton, onClick: props.onCancel }, 'Cancel')
            )
        )
    );
}

function ProjectsView(props) {
    var projectsState = React.useState([]);
    var projects = projectsState[0], setProjects = projectsState[1];
    var formState = React.useState(null);
    var form = formState[0], setForm = formState[1];
    var editingIdState = React.useState(null);
    var editingId = editingIdState[0], setEditingId = editingIdState[1];
    var errorBits = useErrorBanner();
    var errorBanner = errorBits[0], setError = errorBits[1];

    function reload() {
        apiFetch('/projects').then(setProjects).catch(function (e) { setError(e.message); });
    }

    React.useEffect(function () { reload(); }, []);

    function startAdd() {
        setEditingId(null);
        setForm({ projectName: '', uri: '' });
    }

    function startEdit(project) {
        setEditingId(project.id);
        setForm({ projectName: project.projectName, uri: project.uri });
    }

    function submit(evt) {
        evt.preventDefault();
        var body = JSON.stringify(form);
        var request = editingId
            ? apiFetch('/projects/' + editingId, { method: 'PUT', body: body })
            : apiFetch('/projects', { method: 'POST', body: body });
        request.then(function () {
            setForm(null);
            setEditingId(null);
            reload();
        }).catch(function (e) { setError(e.message); });
    }

    function remove(project) {
        if (!window.confirm('Delete project "' + project.projectName + '"?')) return;
        apiFetch('/projects/' + project.id, { method: 'DELETE' })
            .then(reload)
            .catch(function (e) { setError(e.message); });
    }

    return React.createElement('div', { style: styles.page },
        React.createElement('div', { style: styles.headerRow },
            React.createElement('h2', { style: styles.title }, 'Git Projects'),
            React.createElement('button', { style: styles.primaryButton, onClick: startAdd }, '+ New Project')
        ),
        errorBanner,
        React.createElement(ProjectsList, {
            projects: projects,
            onManageUsers: props.onManageUsers,
            onEdit: startEdit,
            onDelete: remove
        }),
        form ? React.createElement(ProjectForm, {
            form: form, setForm: setForm, editing: editingId !== null,
            onSubmit: submit, onCancel: function () { setForm(null); setEditingId(null); }
        }) : null
    );
}

/* -------------------------- Per-project users (drill-down) -------------------------- */

function UserForm(props) {
    var form = props.form, setForm = props.setForm, sshAuth = props.sshAuth;
    return React.createElement('div', { style: styles.panel },
        React.createElement('h3', { style: styles.panelTitle }, props.editing ? 'Edit User' : 'New User'),
        React.createElement('form', { onSubmit: props.onSubmit },
            React.createElement(Field, {
                label: 'Ignition User', required: true, value: form.ignitionUser, disabled: props.editing,
                onChange: function (v) { setForm(Object.assign({}, form, { ignitionUser: v })); }
            }),
            React.createElement(Field, {
                label: 'Email', required: true, value: form.email,
                onChange: function (v) { setForm(Object.assign({}, form, { email: v })); }
            }),
            sshAuth
                ? React.createElement(Field, {
                    label: 'SSH Key', value: form.sshKey,
                    onChange: function (v) { setForm(Object.assign({}, form, { sshKey: v })); }
                })
                : React.createElement(React.Fragment, null,
                    React.createElement(Field, {
                        label: 'Git User Name', required: true, value: form.userName,
                        onChange: function (v) { setForm(Object.assign({}, form, { userName: v })); }
                    }),
                    React.createElement(Field, {
                        label: 'Password', type: 'password', value: form.password,
                        onChange: function (v) { setForm(Object.assign({}, form, { password: v })); }
                    })
                ),
            React.createElement('div', { style: styles.formActions },
                React.createElement('button', { type: 'submit', style: styles.primaryButton }, props.editing ? 'Save' : 'Add User'),
                React.createElement('button', { type: 'button', style: styles.secondaryButton, onClick: props.onCancel }, 'Cancel')
            )
        )
    );
}

function ProjectUsersView(props) {
    var project = props.project;
    var usersState = React.useState([]);
    var users = usersState[0], setUsers = usersState[1];
    var emptyForm = { ignitionUser: '', email: '', userName: '', password: '', sshKey: '' };
    var formState = React.useState(null);
    var form = formState[0], setForm = formState[1];
    var editingUserState = React.useState(null);
    var editingUser = editingUserState[0], setEditingUser = editingUserState[1];
    var errorBits = useErrorBanner();
    var errorBanner = errorBits[0], setError = errorBits[1];

    function reload() {
        apiFetch('/users?projectId=' + project.id).then(setUsers).catch(function (e) { setError(e.message); });
    }

    React.useEffect(function () { reload(); }, [project.id]);

    function startAdd() {
        setEditingUser(null);
        setForm(emptyForm);
    }

    function startEdit(user) {
        setEditingUser(user.ignitionUser);
        setForm({
            ignitionUser: user.ignitionUser,
            email: user.email || '',
            userName: user.userName || '',
            password: '',
            sshKey: ''
        });
    }

    function submit(evt) {
        evt.preventDefault();
        var body = JSON.stringify(Object.assign({}, form, { projectId: project.id }));
        var request = editingUser
            ? apiFetch('/users/' + encodeURIComponent(editingUser), { method: 'PUT', body: body })
            : apiFetch('/users', { method: 'POST', body: body });
        request.then(function () {
            setForm(null);
            setEditingUser(null);
            reload();
        }).catch(function (e) { setError(e.message); });
    }

    function remove(user) {
        if (!window.confirm('Delete git credentials for "' + user.ignitionUser + '"?')) return;
        apiFetch('/users/' + encodeURIComponent(user.ignitionUser), { method: 'DELETE' })
            .then(reload)
            .catch(function (e) { setError(e.message); });
    }

    return React.createElement('div', { style: styles.page },
        React.createElement('div', { style: styles.breadcrumb },
            React.createElement('a', { style: styles.breadcrumbLink, onClick: props.onBack }, 'Git Projects'),
            ' / ' + project.projectName
        ),
        React.createElement('div', { style: styles.headerRow },
            React.createElement('h2', { style: styles.title }, 'Users — ' + project.projectName),
            React.createElement('button', { style: styles.primaryButton, onClick: startAdd }, '+ New User')
        ),
        errorBanner,
        React.createElement('table', { style: styles.table },
            React.createElement('thread', null,
                React.createElement('tr', null,
                    React.createElement('th', { style: styles.th }, 'Ignition User'),
                    React.createElement('th', { style: styles.th }, 'Email'),
                    React.createElement('th', { style: styles.th }, project.sshAuthentication ? 'SSH Key' : 'Git User Name'),
                    React.createElement('th', { style: styles.th }, '')
                )
            ),
            React.createElement('body', null,
                users.length === 0
                    ? React.createElement('tr', null, React.createElement('td', { style: styles.emptyRow, colSpan: 4 }, 'No users configured for this project yet.'))
                    : users.map(function (u) {
                        return React.createElement('tr', { key: u.ignitionUser },
                            React.createElement('td', { style: styles.td }, u.ignitionUser),
                            React.createElement('td', { style: styles.td }, u.email),
                            React.createElement('td', { style: styles.td }, project.sshAuthentication ? (u.hasSSHKey ? 'Configured' : '—') : u.userName),
                            React.createElement('td', { style: styles.td },
                                React.createElement('button', { style: styles.linkAction, onClick: function () { startEdit(u); } }, 'Edit'),
                                ' | ',
                                React.createElement('button', { style: styles.linkActionDanger, onClick: function () { remove(u); } }, 'Delete')
                            )
                        );
                    })
            )
        ),
        form ? React.createElement(UserForm, {
            form: form, setForm: setForm, editing: editingUser !== null, sshAuth: project.sshAuthentication,
            onSubmit: submit, onCancel: function () { setForm(null); setEditingUser(null); }
        }) : null
    );
}

/* -------------------------------------- Root -------------------------------------- */

function GitProjectsPage() {
    var managingState = React.useState(null);
    var managingProject = managingState[0], setManagingProject = managingState[1];

    if (managingProject) {
        return React.createElement(ProjectUsersView, {
            project: managingProject,
            onBack: function () { setManagingProject(null); }
        });
    }
    return React.createElement(ProjectsView, { onManageUsers: setManagingProject });
}

// Property name must exactly match the componentId passed to .mount() in GatewayHook.
module.exports = {
    'git-projects-page': GitProjectsPage
};
