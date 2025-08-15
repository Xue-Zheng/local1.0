'use client';
'use client';

const Footer = () => { const currentYear = new Date().getFullYear();

    return (
        <footer className="bg-black text-white mt-auto">
            <div className="container mx-auto px-4 py-16">
                <div className="border-b border-gray-800 pb-12 grid grid-cols-1 md:grid-cols-3 gap-10">
                    <div>
                        <h4 className="text-lg font-bold mb-6 tracking-wide">Contact Us</h4>
                        <address className="not-italic text-sm space-y-1 text-gray-400 leading-relaxed">
                            <p>E tū Incorporated</p>
                            <p>646 Great South Road</p>
                            <p>Ellerslie, Auckland 1151</p>
                            <p className="mt-4">Phone: 0800 186 466</p>
                            <p>Email: <a href="mailto:support@etu.nz" className="text-blue-400 hover:text-blue-500 transition-colors">support@etu.nz</a></p>
                        </address>
                    </div>
                    <div>
                        <h4 className="text-lg font-bold mb-6 tracking-wide">Quick Links</h4>
                        <ul className="space-y-3 text-sm">
                            <li>
                                <a href="/" className="hover:text-orange-500 transition-colors">
                                    Home
                                </a>
                            </li>
                            <li>
                                <a href="/register" className="hover:text-orange-500 transition-colors">
                                    Register
                                </a>
                            </li>
                            <li>
                                <a href="/ticket" className="hover:text-orange-500 transition-colors">
                                    My Ticket
                                </a>
                            </li>
                        </ul>
                    </div>
                    <div>
                        <h4 className="text-lg font-bold mb-6 tracking-wide">About E tū</h4>
                        <p className="text-sm text-gray-400 leading-relaxed mb-4">
                            E tū is New Zealand's largest private sector union, representing over 48,000 workers across industries including aviation, construction, manufacturing, energy, communications, community support and cleaning.
                        </p>
                        <p className="text-xs text-gray-600">
                            © {currentYear} E tū. All rights reserved.
                        </p>
                    </div>
                </div>
                <div className="pt-8 text-center text-xs text-gray-600">
                    <p>Visit us at <a href="https://etu.nz" target="_blank" rel="noopener noreferrer" className="text-blue-400 hover:text-blue-500 transition-colors">etu.nz</a></p>
                </div>
            </div>
        </footer>
    );
};

export default Footer;